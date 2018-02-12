/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.griffin.parser.model;

import com.questdb.griffin.common.ExprNode;
import com.questdb.griffin.parser.IntervalCompiler;
import com.questdb.griffin.parser.ParserException;
import com.questdb.std.*;

public class IntrinsicModel implements Mutable {
    public static final IntrinsicModelFactory FACTORY = new IntrinsicModelFactory();
    private static final LongList INFINITE_INTERVAL;
    public final CharSequenceHashSet keyValues = new CharSequenceHashSet();
    public final IntList keyValuePositions = new IntList();
    private final LongList intervalsA = new LongList();
    private final LongList intervalsB = new LongList();
    private final LongList intervalsC = new LongList();
    public String keyColumn;
    public ExprNode filter;
    public LongList intervals;
    public int intrinsicValue = IntrinsicValue.UNDEFINED;
    public boolean keyValuesIsLambda = false;

    private IntrinsicModel() {
    }

    @Override
    public void clear() {
        keyColumn = null;
        keyValues.clear();
        keyValuePositions.clear();
        clearInterval();
        filter = null;
        intervals = null;
        intrinsicValue = IntrinsicValue.UNDEFINED;
        keyValuesIsLambda = false;
    }

    public void clearInterval() {
        this.intervals = null;
    }

    public void excludeValue(ExprNode val) {
        String value = Chars.equals("null", val.token) ? null : Chars.stripQuotes(val.token);
        int index = keyValues.remove(value);
        if (index > -1) {
            keyValuePositions.removeIndex(index);
        }

        if (keyValues.size() == 0) {
            intrinsicValue = IntrinsicValue.FALSE;
        }
    }

    public void intersectIntervals(long lo, long hi) {
        LongList temp = shuffleTemp();
        temp.clear();
        temp.add(lo);
        temp.add(hi);
        intersectIntervals(temp);
    }

    public void intersectIntervals(CharSequence seq, int lo, int lim, int position) throws ParserException {
        LongList temp = shuffleTemp();
        temp.clear();
        IntervalCompiler.parseIntervalEx(seq, lo, lim, position, temp);
        intersectIntervals(temp);
    }

    public void subtractIntervals(long lo, long hi) {
        LongList temp = shuffleTemp();
        temp.clear();
        temp.add(lo);
        temp.add(hi);
        subtractIntervals(temp);
    }

    public void subtractIntervals(CharSequence seq, int lo, int lim, int position) throws ParserException {
        LongList temp = shuffleTemp();
        temp.clear();
        IntervalCompiler.parseIntervalEx(seq, lo, lim, position, temp);
        subtractIntervals(temp);
    }

    @Override
    public String toString() {
        return "IntrinsicModel{" +
                "keyValues=" + keyValues +
                ", keyColumn='" + keyColumn + '\'' +
                ", filter=" + filter +
                '}';
    }

    private void intersectIntervals(LongList intervals) {
        if (this.intervals == null) {
            this.intervals = intervals;
        } else {
            final LongList dest = shuffleDest();
            IntervalCompiler.intersect(intervals, this.intervals, dest);
            this.intervals = dest;
        }

        if (this.intervals.size() == 0) {
            intrinsicValue = IntrinsicValue.FALSE;
        }
    }

    private LongList shuffleDest() {
        LongList result = shuffleDest0();
        result.clear();
        return result;
    }

    private LongList shuffleDest0() {
        if (intervals == null || intervals == intervalsA) {
            return intervalsB;
        }

        if (intervals == intervalsB) {
            return intervalsC;
        }

        return intervalsA;
    }

    private LongList shuffleTemp() {
        if (intervals == null || intervals == intervalsA) {
            return intervalsC;
        }

        if (intervals == intervalsB) {
            return intervalsA;
        }

        return intervalsB;
    }

    private void subtractIntervals(LongList temp) {
        IntervalCompiler.invert(temp);
        if (this.intervals == null) {
            intervals = temp;
        } else {
            final LongList dest = shuffleDest();
            dest.clear();
            IntervalCompiler.intersect(temp, this.intervals, dest);
            this.intervals = dest;
        }
        if (this.intervals.size() == 0) {
            intrinsicValue = IntrinsicValue.FALSE;
        }
    }

    public static final class IntrinsicModelFactory implements ObjectFactory<IntrinsicModel> {
        @Override
        public IntrinsicModel newInstance() {
            return new IntrinsicModel();
        }
    }

    static {
        INFINITE_INTERVAL = new LongList();
        INFINITE_INTERVAL.add(Long.MIN_VALUE);
        INFINITE_INTERVAL.add(Long.MAX_VALUE);
    }

}
