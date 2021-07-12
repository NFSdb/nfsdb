/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.groupby;

import io.questdb.cairo.map.MapValue;
import io.questdb.cairo.sql.*;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.SqlExecutionInterruptor;
import io.questdb.griffin.engine.functions.GroupByFunction;
import io.questdb.griffin.engine.functions.TimestampFunction;
import io.questdb.std.Misc;
import io.questdb.std.Numbers;
import io.questdb.std.NumericException;
import io.questdb.std.ObjList;
import io.questdb.std.datetime.TimeZoneRules;
import io.questdb.std.datetime.microtime.TimestampFormatUtils;
import io.questdb.std.datetime.microtime.Timestamps;
import org.jetbrains.annotations.Nullable;

import static io.questdb.std.datetime.TimeZoneRuleFactory.RESOLUTION_MICROS;
import static io.questdb.std.datetime.microtime.Timestamps.MINUTE_MICROS;

public abstract class AbstractNoRecordSampleByCursor implements NoRandomAccessRecordCursor {
    protected final TimestampSampler timestampSampler;
    protected final int timestampIndex;
    protected final ObjList<GroupByFunction> groupByFunctions;
    private final ObjList<Function> recordFunctions;
    protected Record baseRecord;
    protected long sampleLocalEpoch;
    // this epoch is generally the same as `sampleLocalEpoch` except for cases where
    // sampler passed thru Daytime Savings Transition date
    // diverging values tell `filling` implementations not to fill this gap
    protected long nextSampleLocalEpoch;
    protected RecordCursor base;
    protected SqlExecutionInterruptor interruptor;
    protected long tzOffset;
    protected long nextDst;
    protected long fixedOffset;
    protected long topTzOffset;
    protected long localEpoch;
    protected TimeZoneRules rules;
    private long topNextDst;
    private long topLocalEpoch;

    public AbstractNoRecordSampleByCursor(
            ObjList<Function> recordFunctions,
            int timestampIndex, // index of timestamp column in base cursor
            TimestampSampler timestampSampler,
            ObjList<GroupByFunction> groupByFunctions
    ) {
        this.timestampIndex = timestampIndex;
        this.timestampSampler = timestampSampler;
        this.recordFunctions = recordFunctions;
        this.groupByFunctions = groupByFunctions;
    }

    @Override
    public void close() {
        base.close();
        interruptor = null;
    }

    @Override
    public SymbolTable getSymbolTable(int columnIndex) {
        return (SymbolTable) recordFunctions.getQuick(columnIndex);
    }

    @Override
    public void toTop() {
        GroupByUtils.toTop(recordFunctions);
        this.base.toTop();
        this.localEpoch = topLocalEpoch;
        this.sampleLocalEpoch = this.nextSampleLocalEpoch = topLocalEpoch;
        // timezone offset is liable to change when we pass over DST edges
        this.tzOffset = topTzOffset;
        this.nextDst = topNextDst;
    }

    @Override
    public long size() {
        return -1;
    }

    public void of(
            RecordCursor base,
            SqlExecutionContext executionContext,
            Function timezoneNameFunc,
            int timezoneNameFuncPos,
            Function offsetFunc,
            int offsetFuncPos
    ) throws SqlException {
        this.base = base;
        this.baseRecord = base.getRecord();
        final long timestamp = baseRecord.getTimestamp(timestampIndex);
        of(base, timestamp, executionContext, timezoneNameFunc, timezoneNameFuncPos, offsetFunc, offsetFuncPos);
    }

    public void of(
            RecordCursor base,
            long timestamp,
            SqlExecutionContext executionContext,
            Function timezoneNameFunc,
            int timezoneNameFuncPos,
            Function offsetFunc,
            int offsetFuncPos
    ) throws SqlException {
        // factory guarantees that base cursor is not empty
        timezoneNameFunc.init(base, executionContext);
        offsetFunc.init(base, executionContext);

        this.rules = null;
        final CharSequence tz = timezoneNameFunc.getStr(null);
        if (tz != null) {
            try {
                long opt = Timestamps.parseOffset(tz);
                if (opt == Long.MIN_VALUE) {
                    // this is timezone name
                    TimeZoneRules rules = TimestampFormatUtils.enLocale.getZoneRules(
                            Numbers.decodeLowInt(TimestampFormatUtils.enLocale.matchZone(tz, 0, tz.length())),
                            RESOLUTION_MICROS
                    );
                    // fixed rules means the timezone does not have historical or daylight time changes
                    tzOffset = rules.getOffset(timestamp);
                    nextDst = rules.getNextDST(timestamp);
                    this.rules = rules;
                } else {
                    // here timezone is in numeric offset format
                    tzOffset = Numbers.decodeLowInt(opt) * MINUTE_MICROS;
                    nextDst = Long.MAX_VALUE;
                }
            } catch (NumericException e) {
                Misc.free(base);
                throw SqlException.$(timezoneNameFuncPos, "invalid timezone: ").put(tz);
            }
        } else {
            this.tzOffset = 0;
            this.nextDst = Long.MAX_VALUE;
        }

        final CharSequence offset = offsetFunc.getStr(null);

        if (offset != null) {
            final long val = Timestamps.parseOffset(offset);
            if (val == Numbers.LONG_NaN) {
                // bad value for offset
                Misc.free(base);
                throw SqlException.$(offsetFuncPos, "invalid offset: ").put(offset);
            }

            this.fixedOffset = Numbers.decodeLowInt(val) * MINUTE_MICROS;
        } else {
            fixedOffset = Long.MIN_VALUE;
        }

        if (tzOffset == 0 && fixedOffset == Long.MIN_VALUE) {
            // this is the default path, we align time intervals to the first observation
            timestampSampler.setStart(timestamp + tzOffset);
        } else if (fixedOffset > 0) {
            timestampSampler.setStart(timestamp + tzOffset + this.fixedOffset);
        } else {
            timestampSampler.setStart(tzOffset);
        }
        this.topTzOffset = tzOffset;
        this.topNextDst = nextDst;
        this.topLocalEpoch = this.localEpoch = timestampSampler.round(timestamp + tzOffset);
        this.sampleLocalEpoch = this.nextSampleLocalEpoch = localEpoch;
        interruptor = executionContext.getSqlExecutionInterruptor();
    }

    protected long adjustDST(long timestamp, int n, @Nullable MapValue mapValue) {
        final long t = timestamp - tzOffset;
        if (t >= nextDst) {
            final long daylightSavings = rules.getOffset(t);
            nextDst = rules.getNextDST(t);
            if (daylightSavings < tzOffset) {
                // time moved backwards, we need to check if we should be collapsing this
                // hour into previous period or not
                updateValueWhenClockMovesBack(mapValue, n);
                nextSampleLocalEpoch = timestampSampler.round(timestamp);
                localEpoch = nextSampleLocalEpoch;
                sampleLocalEpoch -= (tzOffset - daylightSavings);
                tzOffset = daylightSavings;
                return Long.MIN_VALUE;
            }
            // time moved forward, we need to make sure we move our sample boundary
            nextDst = rules.getNextDST(t);
            timestamp = t + daylightSavings;
            sampleLocalEpoch -= (tzOffset - daylightSavings);
            nextSampleLocalEpoch = sampleLocalEpoch;
            tzOffset = daylightSavings;
        }
        return timestamp;
    }

    protected void updateValueWhenClockMovesBack(MapValue value, int n) {
        GroupByUtils.updateExisting(groupByFunctions, n, value, baseRecord);
    }

    protected boolean notKeyedLoop(MapValue mapValue) {
        long next = timestampSampler.nextTimestamp(this.localEpoch);
        this.sampleLocalEpoch = this.localEpoch;
        this.nextSampleLocalEpoch = this.localEpoch;
        // looks like we need to populate key map
        // at the start of this loop 'lastTimestamp' will be set to timestamp
        // of first record in base cursor
        int n = groupByFunctions.size();
        GroupByUtils.updateNew(groupByFunctions, n, mapValue, baseRecord);
        while (base.hasNext()) {
            long timestamp = getBaseRecordTimestamp();
            if (timestamp < next) {
                GroupByUtils.updateExisting(groupByFunctions, n, mapValue, baseRecord);
                interruptor.checkInterrupted();
            } else {
                // timestamp changed, make sure we keep the value of 'lastTimestamp'
                // unchanged. Timestamp columns uses this variable
                // When map is exhausted we would assign 'next' to 'lastTimestamp'
                // and build another map
                timestamp = adjustDST(timestamp, n, mapValue);
                if (timestamp != Long.MIN_VALUE) {
                    this.localEpoch = timestampSampler.round(timestamp);
                    GroupByUtils.toTop(groupByFunctions);
                    return true;
                }
            }
        }

        // opportunity, after we stream map that is.
        baseRecord = null;
        return true;
    }



    protected long getBaseRecordTimestamp() {
        return baseRecord.getTimestamp(timestampIndex) + tzOffset;
    }

    protected class TimestampFunc extends TimestampFunction implements Function {

        @Override
        public long getTimestamp(Record rec) {
            return sampleLocalEpoch - tzOffset;
        }
    }
}
