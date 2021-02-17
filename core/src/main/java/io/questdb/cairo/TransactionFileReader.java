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

package io.questdb.cairo;

import io.questdb.std.FilesFacade;
import io.questdb.std.IntList;
import io.questdb.std.LongList;
import io.questdb.std.Misc;
import io.questdb.std.datetime.microtime.Timestamps;
import io.questdb.std.str.Path;

import java.io.Closeable;

import static io.questdb.cairo.TableUtils.*;

public class TransactionFileReader implements Closeable {
    protected static final int LONGS_PER_PARTITION = 4;
    protected static final int PARTITION_TS_OFFSET = 0;
    protected static final int PARTITION_SIZE_OFFSET = 1;
    protected static final int PARTITION_TX_OFFSET = 2;

    protected final FilesFacade ff;
    protected final Path path;
    protected final int rootLen;
    protected final LongList attachedPartitions = new LongList();
    protected long minTimestamp;
    protected long maxTimestamp;
    protected long txn;
    protected int symbolsCount;
    protected long dataVersion;
    protected long structureVersion;
    protected long fixedRowCount;
    protected long transientRowCount;
    protected int partitionBy = -1;
    private VirtualMemory roTxMem;
    private ReadOnlyMemory readOnlyTxMem;
    private long partitionTableVersion;
    private Timestamps.TimestampFloorMethod timestampFloorMethod;

    public TransactionFileReader(FilesFacade ff, Path path) {
        this.ff = ff;
        this.path = path;
        this.rootLen = path.length();
    }

    @Override
    public void close() {
        roTxMem = Misc.free(roTxMem);
    }

    public long getAttachedPartitionTimestamp(int i) {
        return attachedPartitions.getQuick(i * LONGS_PER_PARTITION);
    }

    public int getAttachedPartitionsSize() {
        return attachedPartitions.size();
    }

    public long getDataVersion() {
        return dataVersion;
    }

    public long getFixedRowCount() {
        return fixedRowCount;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public long getMinTimestamp() {
        return minTimestamp;
    }

    public long getPartitionTableVersion() {
        return partitionTableVersion;
    }

    public long getStructureVersion() {
        return structureVersion;
    }

    public long getTransientRowCount() {
        return transientRowCount;
    }

    public long getTxn() {
        return txn;
    }

    public void open() {
        assert this.roTxMem == null;
        roTxMem = openTxnFile(ff, path, rootLen);
        if (roTxMem instanceof ReadOnlyMemory) {
            // In readonly mode Tx file has to call grow sometimes
            this.readOnlyTxMem = (ReadOnlyMemory) roTxMem;
        }
    }

    public long getPartitionSizeByPartitionTimestamp(long ts) {
        final int index = findAttachedPartitionIndex(getPartitionLo(ts));
        if (index >= 0) {
            return attachedPartitions.getQuick(index + PARTITION_SIZE_OFFSET);
        }
        return -1;
    }

    public void read() {
        this.txn = roTxMem.getLong(TX_OFFSET_TXN);
        this.transientRowCount = roTxMem.getLong(TX_OFFSET_TRANSIENT_ROW_COUNT);
        this.fixedRowCount = roTxMem.getLong(TX_OFFSET_FIXED_ROW_COUNT);
        this.minTimestamp = roTxMem.getLong(TX_OFFSET_MIN_TIMESTAMP);
        this.maxTimestamp = roTxMem.getLong(TX_OFFSET_MAX_TIMESTAMP);
        this.dataVersion = roTxMem.getLong(TX_OFFSET_DATA_VERSION);
        this.structureVersion = roTxMem.getLong(TX_OFFSET_STRUCT_VERSION);
        this.symbolsCount = roTxMem.getInt(TX_OFFSET_MAP_WRITER_COUNT);
        partitionTableVersion = roTxMem.getLong(TableUtils.TX_OFFSET_PARTITION_TABLE_VERSION);
        loadAttachedPartitions(this.maxTimestamp, this.transientRowCount);
    }

    public long readFixedRowCount() {
        return roTxMem.getLong(TX_OFFSET_FIXED_ROW_COUNT);
    }

    public void readSymbolCounts(IntList symbolCountSnapshot) {
        int symbolMapCount = roTxMem.getInt(TableUtils.TX_OFFSET_MAP_WRITER_COUNT);
        if (symbolMapCount > 0) {
            if (this.readOnlyTxMem != null) {
                this.readOnlyTxMem.grow(TableUtils.getSymbolWriterIndexOffset(symbolMapCount));
            }
            for (int i = 0; i < symbolMapCount; i++) {
                symbolCountSnapshot.add(roTxMem.getInt(TableUtils.getSymbolWriterIndexOffset(i)));
            }
        }
    }

    public int readSymbolWriterIndexOffset(int i) {
        return roTxMem.getInt(getSymbolWriterIndexOffset(i));
    }

    public long readTxn() {
        return roTxMem.getLong(TX_OFFSET_TXN);
    }

    public long readTxnCheck() {
        return roTxMem.getLong(TableUtils.TX_OFFSET_TXN_CHECK);
    }

    public int readWriterCount() {
        return roTxMem.getInt(TX_OFFSET_MAP_WRITER_COUNT);
    }

    public boolean attachedPartitionsContains(long ts) {
        return findAttachedPartitionIndex(ts) >= 0;
    }

    public void initPartitionFloor(Timestamps.TimestampFloorMethod timestampFloorMethod, int partitionBy) {
        assert this.timestampFloorMethod == null;
        this.timestampFloorMethod = timestampFloorMethod;
        this.partitionBy = partitionBy;
    }

    private void loadAttachedPartitions(long maxTimestamp, long transientRowCount) {
        attachedPartitions.clear();
        if (partitionBy != PartitionBy.NONE) {
            int symbolWriterCount = symbolsCount;
            int partitionTableSize = roTxMem.getInt(getPartitionTableSizeOffset(symbolWriterCount));
            if (partitionTableSize > 0) {
                if (this.readOnlyTxMem != null) {
                    this.readOnlyTxMem.grow(TableUtils.getPartitionTableIndexOffset(symbolWriterCount, partitionTableSize));
                }
                for (int i = 0; i < partitionTableSize; i++) {
                    attachedPartitions.add(roTxMem.getLong(getPartitionTableIndexOffset(symbolWriterCount, i)));
                }
            }

            if (maxTimestamp != Long.MIN_VALUE) {
                updateAttachedPartitionSizeByTimestamp(maxTimestamp, transientRowCount);
            }
        }
    }

    protected int updateAttachedPartitionSizeByTimestamp(long maxTimestamp, long partitionSize) {
        long partitionTimestamp = getPartitionLo(maxTimestamp);
        int index = findAttachedPartitionIndex(partitionTimestamp);
        if (index > -1) {
            // Update
            updatePartitionSizeByIndex(index, partitionSize);
            return index;
        }

        return insertPartitionSizeByTimestamp(index, partitionTimestamp, partitionSize);
    }

    private void updatePartitionSizeByIndex(int index, long partitionSize) {
        if (attachedPartitions.getQuick(index + PARTITION_SIZE_OFFSET) != partitionSize) {
            attachedPartitions.set(index + PARTITION_SIZE_OFFSET, partitionSize);
        }
    }

    private int insertPartitionSizeByTimestamp(int index, long partitionTimestamp, long partitionSize) {
        // Insert
        int size = attachedPartitions.size();
        attachedPartitions.extendAndSet(size + LONGS_PER_PARTITION - 1, 0);
        index = -(index + 1);
        if (index < size) {
            // Insert in the middle
            attachedPartitions.arrayCopy(index, index + LONGS_PER_PARTITION, LONGS_PER_PARTITION);
        }

        attachedPartitions.setQuick(index + PARTITION_TS_OFFSET, partitionTimestamp);
        attachedPartitions.setQuick(index + PARTITION_SIZE_OFFSET, partitionSize);
        // Out of order transaction which added this partition
        attachedPartitions.setQuick(index + PARTITION_TX_OFFSET, (index < size) ? txn + 1 : 0);
        return index;
    }

    protected VirtualMemory openTxnFile(FilesFacade ff, Path path, int rootLen) {
        try {
            if (this.ff.exists(this.path.concat(TXN_FILE_NAME).$())) {
                return new ReadOnlyMemory(ff, path, this.ff.getPageSize(), this.ff.getPageSize());
            }
            throw CairoException.instance(ff.errno()).put("Cannot append. File does not exist: ").put(this.path);
        } finally {
            this.path.trimTo(rootLen);
        }
    }

    protected int findAttachedPartitionIndex(long ts) {
        ts = getPartitionLo(ts);
        // Start from the end, usually it will be last partition searched / appended
        int hi = attachedPartitions.size() - LONGS_PER_PARTITION;
        if (hi > -1) {
            long last = attachedPartitions.getQuick(hi);
            if (last < ts) {
                return -(hi + LONGS_PER_PARTITION + 1);
            }
            if (last == ts) {
                return hi;
            }
        }

        return scanIndex(ts, hi);
    }

    private int scanIndex(long ts, int hi) {
        // attachedPartitions should be too small to do binary search, scan backwards
        for (int i = hi - LONGS_PER_PARTITION; i > -1; i -= LONGS_PER_PARTITION) {
            long partitionTs = attachedPartitions.getQuick(i);
            if (partitionTs < ts) {
                return -(i + LONGS_PER_PARTITION + 1);
            }
            if (partitionTs == ts) {
                return i;
            }
        }
        return -1;
    }

    protected long getPartitionLo(long timestamp) {
        return timestampFloorMethod != null ? timestampFloorMethod.floor(timestamp) : Long.MIN_VALUE;
    }
}