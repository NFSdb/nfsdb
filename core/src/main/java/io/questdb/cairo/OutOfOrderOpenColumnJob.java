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

import io.questdb.MessageBus;
import io.questdb.mp.AbstractQueueConsumerJob;
import io.questdb.mp.RingQueue;
import io.questdb.mp.SOUnboundedCountDownLatch;
import io.questdb.mp.Sequence;
import io.questdb.std.Files;
import io.questdb.std.FilesFacade;
import io.questdb.std.Unsafe;
import io.questdb.std.str.Path;
import io.questdb.tasks.OutOfOrderCopyTask;
import io.questdb.tasks.OutOfOrderOpenColumnTask;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

import static io.questdb.cairo.TableUtils.*;
import static io.questdb.cairo.TableWriter.*;

public class OutOfOrderOpenColumnJob extends AbstractQueueConsumerJob<OutOfOrderOpenColumnTask> {

    private final CairoConfiguration configuration;
    private final RingQueue<OutOfOrderCopyTask> outboundQueue;
    private final Sequence outboundPubSeq;

    public OutOfOrderOpenColumnJob(MessageBus messageBus) {
        super(messageBus.getOutOfOrderOpenColumnQueue(), messageBus.getOutOfOrderOpenColumnSubSequence());
        this.configuration = messageBus.getConfiguration();
        this.outboundQueue = messageBus.getOutOfOrderCopyQueue();
        this.outboundPubSeq = messageBus.getOutOfOrderCopyPubSequence();
    }

    public static void openColumn(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            int openColumnMode,
            FilesFacade ff,
            CharSequence pathToTable,
            long partitionTimestamp,
            int partitionBy,
            CharSequence columnName,
            AtomicInteger columnCounter,
            int columnType,
            long timestampMergeIndexAddr,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long srcDataMax,
            long txn,
            int prefixType,
            long prefixLo,
            long prefixHi,
            int mergeType,
            long mergeOOOLo,
            long mergeOOOHi,
            long mergeDataLo,
            long mergeDataHi,
            long mergeLen,
            int suffixType,
            long suffixLo,
            long suffixHi,
            long timestampFd,
            boolean isIndexed,
            AppendMemory srcDataFixColumn,
            AppendMemory srcDataVarColumn,
            SOUnboundedCountDownLatch doneLatch
    ) {
        final Path path = Path.getThreadLocal(pathToTable);
        TableUtils.setPathForPartition(path, partitionBy, partitionTimestamp);
        final int plen = path.length();

        // append jobs do not set value of part counter, we do it here for those
        // todo: cache
        final AtomicInteger partCounter = new AtomicInteger(1);
        switch (openColumnMode) {
            case 1:
                oooOpenMidPartitionForAppend(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        ff,
                        path,
                        plen,
                        columnName,
                        partCounter,
                        columnCounter,
                        columnType,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        srcOooLo,
                        srcOooHi,
                        srcDataMax,
                        isIndexed,
                        timestampFd,
                        doneLatch
                );
                break;
            case 2:
                oooOpenLastPartitionForAppend(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        ff,
                        path,
                        plen,
                        columnName,
                        partCounter,
                        columnCounter,
                        columnType,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        srcOooLo,
                        srcOooHi,
                        isIndexed,
                        srcDataFixColumn,
                        srcDataVarColumn,
                        doneLatch
                );
                break;
            case 3:
                oooOpenMidPartitionForMerge(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        ff,
                        path,
                        plen,
                        columnName,
                        partCounter,
                        columnCounter,
                        columnType,
                        timestampMergeIndexAddr,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        srcOooLo,
                        srcOooHi,
                        srcDataMax,
                        txn,
                        prefixType,
                        prefixLo,
                        prefixHi,
                        mergeType,
                        mergeOOOLo,
                        mergeOOOHi,
                        mergeDataLo,
                        mergeDataHi,
                        mergeLen,
                        suffixType,
                        suffixLo,
                        suffixHi,
                        timestampFd,
                        isIndexed,
                        doneLatch
                );
                break;
            case 4:
                oooOpenLastPartitionForMerge(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        ff,
                        path,
                        plen,
                        columnName,
                        partCounter,
                        columnCounter,
                        columnType,
                        timestampMergeIndexAddr,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        srcOooLo,
                        srcOooHi,
                        srcDataMax,
                        txn,
                        prefixType,
                        prefixLo,
                        prefixHi,
                        mergeType,
                        mergeOOOLo,
                        mergeOOOHi,
                        mergeDataLo,
                        mergeDataHi,
                        mergeLen,
                        suffixType,
                        suffixLo,
                        suffixHi,
                        isIndexed,
                        srcDataFixColumn,
                        srcDataVarColumn,
                        doneLatch
                );
                break;
            case 5:
                oooOpenNewPartitionForAppend(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        ff,
                        path,
                        plen,
                        columnName,
                        partCounter,
                        columnCounter,
                        columnType,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        srcOooLo,
                        srcOooHi,
                        isIndexed,
                        doneLatch
                );
                break;
        }
    }

    private static long mapReadWriteOrFail(FilesFacade ff, @Nullable Path path, long fd, long size) {
        long addr = ff.mmap(fd, size, 0, Files.MAP_RW);
        if (addr != -1) {
            return addr;
        }
        throw CairoException.instance(ff.errno()).put("could not mmap [file=").put(path).put(", fd=").put(fd).put(", size=").put(size).put(']');
    }

    private static long openReadWriteOrFail(FilesFacade ff, Path path) {
        final long fd = ff.openRW(path);
        if (fd != -1) {
            return fd;
        }
        throw CairoException.instance(ff.errno()).put("could not open for append [file=").put(path).put(']');
    }

    private static void truncateToSizeOrFail(FilesFacade ff, @Nullable Path path, long fd, long size) {
        if (ff.isRestrictedFileSystem()) {
            return;
        }
        if (!ff.truncate(fd, size)) {
            throw CairoException.instance(ff.errno()).put("could resize [file=").put(path).put(", size=").put(size).put(", fd=").put(fd).put(']');
        }
    }

    private static long getVarColumnLength(
            long srcLo,
            long srcHi,
            long srcFixAddr,
            long srcFixSize,
            long srcVarSize
    ) {
        final long lo = Unsafe.getUnsafe().getLong(srcFixAddr + srcLo * Long.BYTES);
        final long hi;
        if (srcHi + 1 == srcFixSize / Long.BYTES) {
            hi = srcVarSize;
        } else {
            hi = Unsafe.getUnsafe().getLong(srcFixAddr + (srcHi + 1) * Long.BYTES);
        }
        return hi - lo;
    }

    private static long getVarColumnSize(FilesFacade ff, int columnType, long dataFd, long lastValueOffset) {
        final long addr;
        final long offset;
        if (columnType == ColumnType.STRING) {
            addr = ff.mmap(dataFd, lastValueOffset + Integer.BYTES, 0, Files.MAP_RO);
            final int len = Unsafe.getUnsafe().getInt(addr + lastValueOffset);
            ff.munmap(addr, lastValueOffset + Integer.BYTES);
            if (len < 1) {
                offset = lastValueOffset + Integer.BYTES;
            } else {
                offset = lastValueOffset + Integer.BYTES + len * 2L; // character bytes
            }
        } else {
            // BINARY
            addr = ff.mmap(dataFd, lastValueOffset + Long.BYTES, 0, Files.MAP_RO);
            final long len = Unsafe.getUnsafe().getLong(addr + lastValueOffset);
            ff.munmap(addr, lastValueOffset + Long.BYTES);
            if (len < 1) {
                offset = lastValueOffset + Long.BYTES;
            } else {
                offset = lastValueOffset + Long.BYTES + len;
            }
        }
        return offset;
    }

    private static void oooOpenMidPartitionForAppend(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            FilesFacade ff,
            Path path,
            int plen,
            CharSequence columnName,
            AtomicInteger partCounter,
            AtomicInteger columnCounter,
            int columnType,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long srcDataMax,
            boolean isIndexed,
            long timestampFd,
            SOUnboundedCountDownLatch doneLatch
    ) {
        long dstKFd = 0;
        long dstVFd = 0;
        long dstIndexOffset = 0;
        long dstFixFd;
        long dstFixSize;
        long dstFixAddr;
        long dstFixOffset;
        long dstVarFd = 0;
        long dstVarAddr = 0;
        long dstVarSize = 0;
        long dstVarOffset = 0;

        switch (columnType) {
            case ColumnType.BINARY:
            case ColumnType.STRING:
                // index files are opened as normal
                iFile(path.trimTo(plen), columnName);
                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = (srcOooHi - srcOooLo + 1 + srcDataMax) * Long.BYTES;
                truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixOffset = srcDataMax * Long.BYTES;

                // open data file now
                dFile(path.trimTo(plen), columnName);
                dstVarFd = openReadWriteOrFail(ff, path);
                dstVarOffset = getVarColumnSize(
                        ff,
                        columnType,
                        dstVarFd,
                        Unsafe.getUnsafe().getLong(dstFixAddr + dstFixOffset - Long.BYTES)
                );
                dstVarSize = getVarColumnLength(srcOooLo, srcOooHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize) + dstVarOffset;
                truncateToSizeOrFail(ff, path, dstVarFd, dstVarSize);
                dstVarAddr = mapReadWriteOrFail(ff, path, dstVarFd, dstVarSize);
                break;
            default:
                final int shl = ColumnType.pow2SizeOf(columnType);
                dstFixSize = (srcOooHi - srcOooLo + 1 + srcDataMax) << shl;
                dstFixOffset = srcDataMax << shl;
                if (timestampFd > 0) {
                    dstFixFd = -timestampFd;
                    truncateToSizeOrFail(ff, null, -dstFixFd, dstFixSize);
                    dstFixAddr = mapReadWriteOrFail(ff, null, -dstFixFd, dstFixSize);
                } else {
                    dFile(path.trimTo(plen), columnName);
                    dstFixFd = openReadWriteOrFail(ff, path);
                    truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                    dstFixAddr = mapReadWriteOrFail(ff, null, dstFixFd, dstFixSize);

                    dstIndexOffset = dstFixOffset;
                    if (isIndexed) {
                        BitmapIndexUtils.keyFileName(path.trimTo(plen), columnName);
                        dstKFd = openReadWriteOrFail(ff, path);
                        BitmapIndexUtils.valueFileName(path.trimTo(plen), columnName);
                        dstVFd = openReadWriteOrFail(ff, path);
                    }
                }
                break;
        }

        publishCopyTask(
                configuration,
                outboundQueue,
                outboundPubSeq,
                columnCounter,
                partCounter,
                columnType,
                OO_BLOCK_OO,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                srcOooLo,
                srcOooHi,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                srcOooLo,
                srcOooHi,
                dstFixFd,
                dstFixAddr,
                dstFixOffset,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                dstVarOffset,
                dstVarSize,
                dstKFd,
                dstVFd,
                dstIndexOffset,
                isIndexed,
                doneLatch
        );
    }

    private static void publishCopyTask(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            AtomicInteger columnCounter,
            AtomicInteger partCounter,
            int columnType,
            int blockType,
            long timestampMergeIndexAddr,
            long srcDataFixFd,
            long srcDataFixAddr,
            long srcDataFixSize,
            long srcDataVarFd,
            long srcDataVarAddr,
            long srcDataVarSize,
            long srcDataLo,
            long srcDataHi,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long dstFixFd,
            long dstFixAddr,
            long dstFixOffset,
            long dstFixSize,
            long dstVarFd,
            long dstVarAddr,
            long dstVarOffset,
            long dstVarSize,
            long dstKFd,
            long dstVFd,
            long dstIndexOffset,
            boolean isIndexed,
            SOUnboundedCountDownLatch doneLatch
    ) {
        long cursor = outboundPubSeq.next();
        if (cursor > -1) {
            publishCopyTaskUncontended(
                    outboundQueue,
                    outboundPubSeq,
                    columnCounter,
                    partCounter,
                    columnType,
                    blockType,
                    timestampMergeIndexAddr,
                    srcDataFixFd,
                    srcDataFixAddr,
                    srcDataFixSize,
                    srcDataVarFd,
                    srcDataVarAddr,
                    srcDataVarSize,
                    srcDataLo,
                    srcDataHi,
                    srcOooFixAddr,
                    srcOooFixSize,
                    srcOooVarAddr,
                    srcOooVarSize,
                    srcOooLo,
                    srcOooHi,
                    dstFixFd,
                    dstFixAddr,
                    dstFixOffset,
                    dstFixSize,
                    dstVarFd,
                    dstVarAddr,
                    dstVarOffset,
                    dstVarSize,
                    dstKFd,
                    dstVFd,
                    dstIndexOffset,
                    isIndexed,
                    cursor,
                    doneLatch
            );
        } else {
            publishCopyTaskContended(
                    configuration,
                    outboundQueue,
                    outboundPubSeq,
                    columnCounter,
                    partCounter,
                    columnType,
                    blockType,
                    timestampMergeIndexAddr,
                    srcDataFixFd,
                    srcDataFixAddr,
                    srcDataFixSize,
                    srcDataVarFd,
                    srcDataVarAddr,
                    srcDataVarSize,
                    srcDataLo,
                    srcDataHi,
                    srcOooFixAddr,
                    srcOooFixSize,
                    srcOooVarAddr,
                    srcOooVarSize,
                    srcOooLo,
                    srcOooHi,
                    dstFixFd,
                    dstFixAddr,
                    dstFixOffset,
                    dstFixSize,
                    dstVarFd,
                    dstVarAddr,
                    dstVarOffset,
                    dstVarSize,
                    dstKFd,
                    dstVFd,
                    dstIndexOffset,
                    isIndexed,
                    cursor,
                    doneLatch
            );
        }

    }

    private static void publishCopyTaskContended(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            AtomicInteger columnCounter,
            AtomicInteger partCounter,
            int columnType,
            int blockType,
            long timestampMergeIndexAddr,
            long srcDataFixFd,
            long srcDataFixAddr,
            long srcDataFixSize,
            long srcDataVarFd,
            long srcDataVarAddr,
            long srcDataVarSize,
            long srcDataLo,
            long srcDataHi,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long dstFixFd,
            long dstFixAddr,
            long dstFixOffset,
            long dstFixSize,
            long dstVarFd,
            long dstVarAddr,
            long dstVarOffset,
            long dstVarSize,
            long dstKFd,
            long dstVFd,
            long dstIndexOffset,
            boolean isIndexed,
            long cursor,
            SOUnboundedCountDownLatch doneLatch
    ) {
        while (cursor == -2) {
            cursor = outboundPubSeq.next();
        }

        if (cursor == -1) {
            OutOfOrderCopyJob.copy(
                    configuration,
                    columnCounter,
                    partCounter,
                    blockType,
                    srcDataFixFd,
                    srcDataFixAddr,
                    srcDataFixSize,
                    srcDataVarFd,
                    srcDataVarAddr,
                    srcDataVarSize,
                    srcDataLo,
                    srcDataHi,
                    srcOooFixAddr,
                    srcOooFixSize,
                    srcOooVarAddr,
                    srcOooVarSize,
                    srcOooLo,
                    srcOooHi,
                    dstFixFd,
                    dstFixAddr,
                    dstFixOffset,
                    dstFixSize,
                    dstVarFd,
                    dstVarAddr,
                    dstVarOffset,
                    dstVarSize,
                    columnType,
                    timestampMergeIndexAddr,
                    dstKFd,
                    dstVFd,
                    dstIndexOffset,
                    isIndexed,
                    doneLatch
            );
        } else {
            publishCopyTaskUncontended(
                    outboundQueue,
                    outboundPubSeq,
                    columnCounter,
                    partCounter,
                    columnType,
                    blockType,
                    timestampMergeIndexAddr,
                    srcDataFixFd,
                    srcDataFixAddr,
                    srcDataFixSize,
                    srcDataVarFd,
                    srcDataVarAddr,
                    srcDataVarSize,
                    srcDataLo,
                    srcDataHi,
                    srcOooFixAddr,
                    srcOooFixSize,
                    srcOooVarAddr,
                    srcOooVarSize,
                    srcOooLo,
                    srcOooHi,
                    dstFixFd,
                    dstFixAddr,
                    dstFixOffset,
                    dstFixSize,
                    dstVarFd,
                    dstVarAddr,
                    dstVarOffset,
                    dstVarSize,
                    dstKFd,
                    dstVFd,
                    dstIndexOffset,
                    isIndexed,
                    cursor,
                    doneLatch
            );
        }
    }

    private static void publishCopyTaskUncontended(
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            AtomicInteger columnCounter,
            AtomicInteger partCounter,
            int columnType,
            int blockType,
            long timestampMergeIndexAddr,
            long srcDataFixFd,
            long srcDataFixAddr,
            long srcDataFixSize,
            long srcDataVarFd,
            long srcDataVarAddr,
            long srcDataVarSize,
            long srcDataLo,
            long srcDataHi,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long dstFixFd,
            long dstFixAddr,
            long dstFixOffset,
            long dstFixSize,
            long dstVarFd,
            long dstVarAddr,
            long dstVarOffset,
            long dstVarSize,
            long dstKFd,
            long dstVFd,
            long dstIndexOffset,
            boolean isIndexed,
            long cursor,
            SOUnboundedCountDownLatch doneLatch
    ) {
        OutOfOrderCopyTask task = outboundQueue.get(cursor);
        task.of(
                columnCounter,
                partCounter,
                columnType,
                blockType,
                timestampMergeIndexAddr,
                srcDataFixFd,
                srcDataFixAddr,
                srcDataFixSize,
                srcDataVarFd,
                srcDataVarAddr,
                srcDataVarSize,
                srcDataLo,
                srcDataHi,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                srcOooLo,
                srcOooHi,
                dstFixFd,
                dstFixAddr,
                dstFixOffset,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                dstVarOffset,
                dstVarSize,
                dstKFd,
                dstVFd,
                dstIndexOffset,
                isIndexed,
                doneLatch
        );
        outboundPubSeq.done(cursor);
    }

    public static void appendTxnToPath(Path path, long txn) {
        path.put("-n-").put(txn);
    }

    private static void createDirsOrFail(FilesFacade ff, Path path, int mkDirMode) {
        if (ff.mkdirs(path, mkDirMode) != 0) {
            throw CairoException.instance(ff.errno()).put("could not create directories [file=").put(path).put(']');
        }
    }

    private static void oooOpenLastPartitionForAppend(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            FilesFacade ff,
            Path path,
            int plen,
            CharSequence columnName,
            AtomicInteger partCounter,
            AtomicInteger columnCounter,
            int columnType,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            boolean isIndexed,
            AppendMemory srcDataFixColumn,
            AppendMemory srcDataVarColumn,
            SOUnboundedCountDownLatch doneLatch
    ) {
        long dstFixFd;
        long dstFixAddr;
        long dstFixOffset;
        long dstFixSize;
        long dstVarFd = 0;
        long dstVarSize = 0;
        long dstVarAddr = 0;
        long dstVarOffset = 0;
        long dstKFd = 0;
        long dstVFd = 0;
        long dstIndexOffset = 0;

        switch (columnType) {
            case ColumnType.BINARY:
            case ColumnType.STRING:
                dstFixOffset = srcDataVarColumn.getAppendOffset();
                dstFixFd = -srcDataVarColumn.getFd();
                dstFixSize = (srcOooHi - srcOooLo + 1) * Long.BYTES + dstFixOffset;
                truncateToSizeOrFail(ff, null, -dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, null, -dstFixFd, dstFixSize);

                dstVarFd = -srcDataFixColumn.getFd();
                dstVarOffset = srcDataFixColumn.getAppendOffset();
                dstVarSize = dstVarOffset + getVarColumnLength(srcOooLo, srcOooHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                truncateToSizeOrFail(ff, null, -dstVarFd, dstVarSize);
                dstVarAddr = mapReadWriteOrFail(ff, null, -dstVarFd, dstVarSize);
                break;
            default:
                long oooSize = (srcOooHi - srcOooLo + 1) << ColumnType.pow2SizeOf(columnType);
                dstFixOffset = srcDataFixColumn.getAppendOffset();
                dstFixFd = -srcDataFixColumn.getFd();
                dstFixSize = oooSize + dstFixOffset;
                truncateToSizeOrFail(ff, null, -dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, null, -dstFixFd, dstFixSize);
                if (isIndexed) {
                    BitmapIndexUtils.keyFileName(path.trimTo(plen), columnName);
                    dstKFd = openReadWriteOrFail(ff, path);
                    BitmapIndexUtils.valueFileName(path.trimTo(plen), columnName);
                    dstVFd = openReadWriteOrFail(ff, path);
                    dstIndexOffset = dstFixOffset;
                }
                break;
        }

        publishCopyTask(
                configuration,
                outboundQueue,
                outboundPubSeq,
                columnCounter,
                partCounter,
                columnType,
                OO_BLOCK_OO,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                srcOooLo,
                srcOooHi,
                dstFixFd,
                dstFixAddr,
                dstFixOffset,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                dstVarOffset,
                dstVarSize,
                dstKFd,
                dstVFd,
                dstIndexOffset,
                isIndexed,
                doneLatch
        );
    }

    private static void oooOpenLastPartitionForMerge(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            FilesFacade ff,
            Path path,
            int plen,
            CharSequence columnName,
            AtomicInteger partCounter,
            AtomicInteger columnCounter,
            int columnType,
            long timestampMergeIndexAddr,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long srcDataMax,
            long txn,
            int prefixType,
            long prefixLo,
            long prefixHi,
            int mergeType,
            long mergeOOOLo,
            long mergeOOOHi,
            long mergeDataLo,
            long mergeDataHi,
            long mergeLen,
            int suffixType,
            long suffixLo,
            long suffixHi,
            boolean isIndexed,
            AppendMemory srcDataFixColumn,
            AppendMemory srcDataVarColumn,
            SOUnboundedCountDownLatch doneLatch
    ) {
        long srcDataFixFd;
        long srcDataFixAddr;
        long srcDataFixSize;
        long srcDataVarFd = 0;
        long srcDataVarAddr = 0;
        long srcDataVarSize = 0;
        long dstFixFd;
        long dstFixSize;
        long dstFixAddr;
        long dstVarFd = 0;
        long dstVarAddr = 0;
        long dstVarSize = 0;
        long dstFixAppendOffset1;
        long dstFixAppendOffset2;
        long dstVarAppendOffset1 = 0;
        long dstVarAppendOffset2 = 0;
        long dstKFd = 0;
        long dstVFd = 0;
        int partCount = 0;

        switch (columnType) {
            case ColumnType.BINARY:
            case ColumnType.STRING:
                // index files are opened as normal
                iFile(path.trimTo(plen), columnName);
                srcDataFixFd = -srcDataVarColumn.getFd();
                srcDataFixAddr = srcDataVarColumn.getAppendOffset();
                srcDataFixSize = mapReadWriteOrFail(ff, path, Math.abs(srcDataFixFd), srcDataFixAddr);

                // open data file now
                dFile(path.trimTo(plen), columnName);
                srcDataVarFd = -srcDataFixColumn.getFd();
                srcDataVarSize = srcDataFixColumn.getAppendOffset();
                srcDataVarAddr = mapReadWriteOrFail(ff, path, Math.abs(srcDataVarFd), srcDataVarSize);

                appendTxnToPath(path.trimTo(plen), txn);
                path.concat(columnName);
                final int pColNameLen = path.length();

                path.put(FILE_SUFFIX_I).$();
                createDirsOrFail(ff, path, configuration.getMkDirMode());
                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = (srcOooHi - srcOooLo + 1 + srcDataMax) * Long.BYTES;
                truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);

                path.trimTo(pColNameLen);
                path.put(FILE_SUFFIX_D).$();
                createDirsOrFail(ff, path, configuration.getMkDirMode());
                dstVarFd = openReadWriteOrFail(ff, path);
                dstVarSize = srcDataVarSize + getVarColumnLength(srcOooLo, srcOooHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                truncateToSizeOrFail(ff, path, dstVarFd, dstVarSize);
                dstVarAddr = mapReadWriteOrFail(ff, path, dstVarFd, dstVarSize);

                // append offset is 0
                //MergeStruct.setDestAppendOffsetFromOffset0(mergeStruct, varColumnStructOffset, 0L);

                // configure offsets
                switch (prefixType) {
                    case OO_BLOCK_OO:
                        dstVarAppendOffset1 = getVarColumnLength(prefixLo, prefixHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                        partCount++;
                        break;
                    case OO_BLOCK_DATA:
                        dstVarAppendOffset1 = getVarColumnLength(prefixLo, prefixHi, srcDataFixAddr, srcDataFixSize, srcDataVarSize);
                        partCount++;
                        break;
                    default:
                        break;
                }

                dstFixAppendOffset1 = (prefixHi - prefixLo + 1) * Long.BYTES;

                // offset 2
                if (mergeDataLo > -1 && mergeOOOLo > -1) {
                    long oooLen = getVarColumnLength(mergeOOOLo, mergeOOOHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                    long dataLen = getVarColumnLength(mergeDataLo, mergeDataHi, srcDataFixAddr, srcDataFixSize, srcDataVarSize);
                    dstFixAppendOffset2 = dstFixAppendOffset1 + (mergeLen * Long.BYTES);
                    dstVarAppendOffset2 = dstVarAppendOffset1 + oooLen + dataLen;
                } else {
                    dstFixAppendOffset2 = dstFixAppendOffset1;
                    dstVarAppendOffset2 = dstVarAppendOffset1;
                }

                break;
            default:
                srcDataFixFd = -srcDataFixColumn.getFd();
                final int shl = ColumnType.pow2SizeOf(columnType);
                dFile(path.trimTo(plen), columnName);
                srcDataFixSize = srcDataFixColumn.getAppendOffset();
                srcDataFixAddr = mapReadWriteOrFail(ff, path, -srcDataFixFd, srcDataFixSize);

                appendTxnToPath(path.trimTo(plen), txn);
                final int pDirNameLen = path.length();

                path.concat(columnName).put(FILE_SUFFIX_D).$();
                createDirsOrFail(ff, path, configuration.getMkDirMode());

                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = ((srcOooHi - srcOooLo + 1) + srcDataMax) << shl;
                truncateToSizeOrFail(ff, path, Math.abs(dstFixFd), dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, Math.abs(dstFixFd), dstFixSize);
                // configure offsets for fixed columns
                dstFixAppendOffset1 = (prefixHi - prefixLo + 1) << shl;
                if (mergeDataLo > -1 && mergeOOOLo > -1) {
                    dstFixAppendOffset2 = dstFixAppendOffset1 + (mergeLen << shl);
                } else {
                    dstFixAppendOffset2 = dstFixAppendOffset1;
                }

                if (isIndexed) {
                    BitmapIndexUtils.keyFileName(path.trimTo(pDirNameLen), columnName);
                    dstKFd = openReadWriteOrFail(ff, path);
                    BitmapIndexUtils.valueFileName(path.trimTo(pDirNameLen), columnName);
                    dstVFd = openReadWriteOrFail(ff, path);
                }

                if (prefixType != OO_BLOCK_NONE) {
                    partCount++;
                }

                break;
        }

        if (mergeType != OO_BLOCK_NONE) {
            partCount++;
        }

        if (suffixType != OO_BLOCK_NONE) {
            partCount++;
        }

        publishMultiCopyTasks(
                configuration,
                outboundQueue,
                outboundPubSeq,
                partCount,
                columnCounter,
                partCounter,
                columnType,
                timestampMergeIndexAddr,
                srcDataFixFd,
                srcDataFixAddr,
                srcDataFixSize,
                srcDataVarFd,
                srcDataVarAddr,
                srcDataVarSize,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                prefixType,
                prefixLo,
                prefixHi,
                mergeType,
                mergeDataLo,
                mergeDataHi,
                mergeOOOLo,
                mergeOOOHi,
                suffixType,
                suffixLo,
                suffixHi,
                dstFixFd,
                dstFixAddr,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                dstVarSize,
                dstFixAppendOffset1,
                dstFixAppendOffset2,
                dstVarAppendOffset1,
                dstVarAppendOffset2,
                dstKFd,
                dstVFd,
                isIndexed,
                doneLatch
        );
    }

    private static void oooOpenMidPartitionForMerge(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            FilesFacade ff,
            Path path,
            int plen,
            CharSequence columnName,
            AtomicInteger partCounter,
            AtomicInteger columnCounter,
            int columnType,
            long timestampMergeIndexAddr,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            long srcDataMax,
            long txn,
            int prefixType,
            long prefixLo,
            long prefixHi,
            int mergeType,
            long mergeOOOLo,
            long mergeOOOHi,
            long mergeDataLo,
            long mergeDataHi,
            long mergeLen,
            int suffixType,
            long suffixLo,
            long suffixHi,
            long timestampFd,
            boolean isIndexed,
            SOUnboundedCountDownLatch doneLatch
    ) {
        long srcDataFixFd;
        long srcDataFixAddr;
        long srcDataFixSize;
        long srcDataVarFd = 0;
        long srcDataVarAddr = 0;
        long srcDataVarSize = 0;
        long dstFixFd;
        long dstFixSize;
        long dstFixAddr;
        long dstVarFd = 0;
        long dstVarAddr = 0;
        long dstVarSize = 0;
        long dstKFd = 0;
        long dstVFd = 0;
        int partCount = 0;
        long dstFixAppendOffset1;
        long dstFixAppendOffset2;
        long dstVarAppendOffset1 = 0;
        long dstVarAppendOffset2 = 0;

        switch (columnType) {
            case ColumnType.BINARY:
            case ColumnType.STRING:
                iFile(path.trimTo(plen), columnName);
                srcDataFixFd = openReadWriteOrFail(ff, path);
                srcDataFixSize = srcDataMax * Long.BYTES;
                srcDataFixAddr = mapReadWriteOrFail(ff, path, srcDataFixFd, srcDataFixSize);

                dFile(path.trimTo(plen), columnName);
                srcDataVarFd = openReadWriteOrFail(ff, path);
                srcDataVarSize = getVarColumnSize(
                        ff,
                        columnType,
                        srcDataVarFd,
                        Unsafe.getUnsafe().getLong(srcDataFixAddr + srcDataFixSize - Long.BYTES)
                );
                srcDataVarAddr = mapReadWriteOrFail(ff, path, srcDataVarFd, srcDataVarSize);

                appendTxnToPath(path.trimTo(plen), txn);
                oooSetPathAndEnsureDir(ff, path, columnName, FILE_SUFFIX_I, configuration.getMkDirMode());

                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = (srcOooHi - srcOooLo + 1 + srcDataMax) * Long.BYTES;
                truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);

                appendTxnToPath(path.trimTo(plen), txn);
                oooSetPathAndEnsureDir(ff, path, columnName, FILE_SUFFIX_D, configuration.getMkDirMode());
                dstVarSize = srcDataVarSize + getVarColumnLength(srcOooLo, srcOooHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                dstVarFd = openReadWriteOrFail(ff, path);
                truncateToSizeOrFail(ff, path, dstVarFd, dstVarSize);
                dstVarAddr = mapReadWriteOrFail(ff, path, dstVarFd, dstVarSize);

                // configure offsets
                switch (prefixType) {
                    case OO_BLOCK_OO:
                        dstVarAppendOffset1 = getVarColumnLength(prefixLo, prefixHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                        partCount++;
                        break;
                    case OO_BLOCK_DATA:
                        partCount++;
                        dstVarAppendOffset1 = getVarColumnLength(prefixLo, prefixHi, srcDataFixAddr, srcDataFixSize, srcDataVarSize);
                        break;
                    default:
                        dstVarAppendOffset1 = 0;
                        break;
                }

                dstFixAppendOffset1 = (prefixHi - prefixLo + 1) * Long.BYTES;

                dstVarAppendOffset2 = dstVarAppendOffset1;
                dstFixAppendOffset2 = dstFixAppendOffset1;
                // offset 2
                if (mergeDataLo > -1 && mergeOOOLo > -1) {
                    final long oooLen = getVarColumnLength(mergeOOOLo, mergeOOOHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                    final long dataLen = getVarColumnLength(mergeDataLo, mergeDataHi, srcDataFixAddr, srcDataFixSize, srcDataVarSize);
                    dstVarAppendOffset2 += oooLen + dataLen;
                    dstFixAppendOffset2 += mergeLen * Long.BYTES;
                    partCount++;
                }

                if (suffixType != OO_BLOCK_NONE) {
                    partCount++;
                }

                break;
            default:
                if (timestampFd > 0) {
                    // ensure timestamp srcDataFixFd is always negative, we will close it externally
                    srcDataFixFd = -timestampFd;
                } else {
                    dFile(path.trimTo(plen), columnName);
                    srcDataFixFd = openReadWriteOrFail(ff, path);
                }

                final int shl = ColumnType.pow2SizeOf(Math.abs(columnType));
                srcDataFixSize = srcDataMax << shl;
                dFile(path.trimTo(plen), columnName);
                srcDataFixAddr = mapReadWriteOrFail(ff, path, Math.abs(srcDataFixFd), srcDataFixSize);

                appendTxnToPath(path.trimTo(plen), txn);
                final int pDirNameLen = path.length();

                path.concat(columnName).put(FILE_SUFFIX_D).$();
                createDirsOrFail(ff, path, configuration.getMkDirMode());

                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = ((srcOooHi - srcOooLo + 1) + srcDataMax) << shl;
                truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);

                dstFixAppendOffset1 = (prefixHi - prefixLo + 1) << shl;
                if (mergeDataLo > -1 && mergeOOOLo > -1) {
                    dstFixAppendOffset2 = dstFixAppendOffset1 + (mergeLen << shl);
                } else {
                    dstFixAppendOffset2 = dstFixAppendOffset1;
                }

                // we have "src" index
                if (isIndexed) {
                    BitmapIndexUtils.keyFileName(path.trimTo(pDirNameLen), columnName);
                    dstKFd = openReadWriteOrFail(ff, path);
                    BitmapIndexUtils.valueFileName(path.trimTo(pDirNameLen), columnName);
                    dstVFd = openReadWriteOrFail(ff, path);
                }

                if (prefixType != OO_BLOCK_NONE) {
                    partCount++;
                }

                if (mergeType != OO_BLOCK_NONE) {
                    partCount++;
                }

                if (suffixType != OO_BLOCK_NONE) {
                    partCount++;
                }

                break;
        }

        publishMultiCopyTasks(
                configuration,
                outboundQueue,
                outboundPubSeq,
                partCount,
                columnCounter,
                partCounter,
                columnType,
                timestampMergeIndexAddr,
                srcDataFixFd,
                srcDataFixAddr,
                srcDataFixSize,
                srcDataVarFd,
                srcDataVarAddr,
                srcDataVarSize,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                prefixType,
                prefixLo,
                prefixHi,
                mergeType,
                mergeDataLo,
                mergeDataHi,
                mergeOOOLo,
                mergeOOOHi,
                suffixType,
                suffixLo,
                suffixHi,
                dstFixFd,
                dstFixAddr,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                dstVarSize,
                dstFixAppendOffset1,
                dstFixAppendOffset2,
                dstVarAppendOffset1,
                dstVarAppendOffset2,
                dstKFd,
                dstVFd,
                isIndexed,
                doneLatch
        );
    }

    private static void oooOpenNewPartitionForAppend(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            FilesFacade ff,
            Path path,
            int plen,
            CharSequence columnName,
            AtomicInteger partCounter,
            AtomicInteger columnCounter,
            int columnType,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            long srcOooLo,
            long srcOooHi,
            boolean isIndexed,
            SOUnboundedCountDownLatch doneLatch
    ) {
        final long dstFixFd;
        final long dstFixAddr;
        final long dstFixSize;
        long dstVarFd = 0;
        long dstVarAddr = 0;
        long dstVarSize = 0;
        long dstKFd = 0;
        long dstVFd = 0;

        switch (columnType) {
            case ColumnType.BINARY:
            case ColumnType.STRING:
                oooSetPathAndEnsureDir(ff, path.trimTo(plen), columnName, FILE_SUFFIX_I, configuration.getMkDirMode());
                dstFixFd = openReadWriteOrFail(ff, path);
                truncateToSizeOrFail(ff, path, dstFixFd, (srcOooHi - srcOooLo + 1) * Long.BYTES);
                dstFixSize = (srcOooHi - srcOooLo + 1) * Long.BYTES;
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);

                oooSetPathAndEnsureDir(ff, path.trimTo(plen), columnName, FILE_SUFFIX_D, configuration.getMkDirMode());
                dstVarFd = openReadWriteOrFail(ff, path);
                dstVarSize = getVarColumnLength(srcOooLo, srcOooHi, srcOooFixAddr, srcOooFixSize, srcOooVarSize);
                truncateToSizeOrFail(ff, path, dstVarFd, dstVarSize);
                dstVarAddr = mapReadWriteOrFail(ff, path, dstVarFd, dstVarSize);
                break;
            default:
                oooSetPathAndEnsureDir(ff, path.trimTo(plen), columnName, FILE_SUFFIX_D, configuration.getMkDirMode());
                dstFixFd = openReadWriteOrFail(ff, path);
                dstFixSize = (srcOooHi - srcOooLo + 1) << ColumnType.pow2SizeOf(Math.abs(columnType));
                truncateToSizeOrFail(ff, path, dstFixFd, dstFixSize);
                dstFixAddr = mapReadWriteOrFail(ff, path, dstFixFd, dstFixSize);
                if (isIndexed) {
                    BitmapIndexUtils.keyFileName(path.trimTo(plen), columnName);
                    dstKFd = openReadWriteOrFail(ff, path);
                    BitmapIndexUtils.valueFileName(path.trimTo(plen), columnName);
                    dstVFd = openReadWriteOrFail(ff, path);
                }
                break;
        }

        publishCopyTask(
                configuration,
                outboundQueue,
                outboundPubSeq,
                columnCounter,
                partCounter,
                columnType,
                OO_BLOCK_OO,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                srcOooLo,
                srcOooHi,
                dstFixFd,
                dstFixAddr,
                0,
                dstFixSize,
                dstVarFd,
                dstVarAddr,
                0,
                dstVarSize,
                dstKFd,
                dstVFd,
                0,
                isIndexed,
                doneLatch
        );
    }

    private static void oooSetPathAndEnsureDir(FilesFacade ff, Path path, CharSequence columnName, CharSequence suffix, int mkDirMode) {
        createDirsOrFail(ff, path.concat(columnName).put(suffix).$(), mkDirMode);
    }

    private static void publishMultiCopyTasks(
            CairoConfiguration configuration,
            RingQueue<OutOfOrderCopyTask> outboundQueue,
            Sequence outboundPubSeq,
            int partCount,
            AtomicInteger columnCounter,
            AtomicInteger partCounter,
            int columnType,
            long timestampMergeIndexAddr,
            long srcDataFixFd,
            long srcDataFixAddr,
            long srcDataFixSize,
            long srcDataVarFd,
            long srcDataVarAddr,
            long srcDataVarSize,
            long srcOooFixAddr,
            long srcOooFixSize,
            long srcOooVarAddr,
            long srcOooVarSize,
            int prefixType,
            long prefixLo,
            long prefixHi,
            int mergeType,
            long mergeDataLo,
            long mergeDataHi,
            long mergeOOOLo,
            long mergeOOOHi,
            int suffixType,
            long suffixLo,
            long suffixHi,
            long dstFixFd,
            long dstFixAddr,
            long dstFixSize,
            long dstVarFd,
            long dstVarAddr,
            long dstVarSize,
            long dstFixAppendOffset1,
            long dstFixAppendOffset2,
            long dstVarAppendOffset1,
            long dstVarAppendOffset2,
            long dstKFd,
            long dstVFd,
            boolean isIndexed,
            SOUnboundedCountDownLatch doneLatch
    ) {
        partCounter.set(partCount);
        switch (prefixType) {
            case OO_BLOCK_OO:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        prefixType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        0,
                        0,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        prefixLo,
                        prefixHi,
                        dstFixFd,
                        dstFixAddr,
                        0,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        0,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            case OO_BLOCK_DATA:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        prefixType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        prefixLo,
                        prefixHi,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        dstFixFd,
                        dstFixAddr,
                        0,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        0,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            default:
                break;
        }

        switch (mergeType) {
            case OO_BLOCK_OO:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        mergeType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        0,
                        0,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        mergeOOOLo,
                        mergeOOOHi,
                        dstFixFd,
                        dstFixAddr,
                        dstFixAppendOffset1,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        dstVarAppendOffset1,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            case OO_BLOCK_DATA:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        mergeType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        mergeDataLo,
                        mergeDataHi,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        dstFixFd,
                        dstFixAddr,
                        dstFixAppendOffset1,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        dstVarAppendOffset1,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            case OO_BLOCK_MERGE:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        mergeType,
                        timestampMergeIndexAddr,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        mergeDataLo,
                        mergeDataHi,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        mergeOOOLo,
                        mergeOOOHi,
                        dstFixFd,
                        dstFixAddr,
                        dstFixAppendOffset1,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        dstVarAppendOffset1,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            default:
                break;
        }

        switch (suffixType) {
            case OO_BLOCK_OO:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        suffixType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        0,
                        0,
                        srcOooFixAddr,
                        srcOooFixSize,
                        srcOooVarAddr,
                        srcOooVarSize,
                        suffixLo,
                        suffixHi,
                        dstFixFd,
                        dstFixAddr,
                        dstFixAppendOffset2,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        dstVarAppendOffset2,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            case OO_BLOCK_DATA:
                publishCopyTask(
                        configuration,
                        outboundQueue,
                        outboundPubSeq,
                        columnCounter,
                        partCounter,
                        columnType,
                        prefixType,
                        0,
                        srcDataFixFd,
                        srcDataFixAddr,
                        srcDataFixSize,
                        srcDataVarFd,
                        srcDataVarAddr,
                        srcDataVarSize,
                        suffixLo,
                        suffixHi,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        dstFixFd,
                        dstFixAddr,
                        dstFixAppendOffset2,
                        dstFixSize,
                        dstVarFd,
                        dstVarAddr,
                        dstVarAppendOffset2,
                        dstVarSize,
                        dstKFd,
                        dstVFd,
                        0,
                        isIndexed,
                        doneLatch
                );
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean doRun(int workerId, long cursor) {
        OutOfOrderOpenColumnTask task = queue.get(cursor);
        // copy task on stack so that publisher has fighting chance of
        // publishing all it has to the queue

        final boolean locked = task.tryLock();
        if (locked) {
            openColumn(task, cursor, subSeq);
        } else {
            subSeq.done(cursor);
        }

        return true;
    }

    private void openColumn(OutOfOrderOpenColumnTask task, long cursor, Sequence subSeq) {
        final int openColumnMode = task.getOpenColumnMode();
        final CharSequence pathToTable = task.getPathToTable();
        final long partitionTimestamp = task.getPartitionTimestamp();
        final int partitionBy = task.getPartitionBy();
        final int columnType = task.getColumnType();
        final CharSequence columnName = task.getColumnName();
        final FilesFacade ff = task.getFf();
        final long srcOooLo = task.getSrcOooLo();
        final long srcOooHi = task.getSrcOooHi();
        final long srcDataMax = task.getSrcDataMax();
        final long timestampFd = task.getTimestampFd();
        final AtomicInteger columnCounter = task.getColumnCounter();
        final boolean isIndexed = task.isIndexed();
        final AppendMemory srcDataFixColumn = task.getSrcDataFixColumn();
        final AppendMemory srcDataVarColumn = task.getSrcDataVarColumn();
        final long srcOooFixAddr = task.getSrcOooFixAddr();
        final long srcOooFixSize = task.getSrcOooFixSize();
        final long srcOooVarAddr = task.getSrcOooVarAddr();
        final long srcOooVarSize = task.getSrcOooVarSize();
        final long mergeOOOLo = task.getMergeOOOLo();
        final long mergeOOOHi = task.getMergeOOOHi();
        final long mergeDataLo = task.getMergeDataLo();
        final long mergeDataHi = task.getMergeDataHi();
        final long mergeLen = mergeOOOHi - mergeOOOLo + 1 + mergeDataHi - mergeDataLo + 1;
        final long txn = task.getTxn();
        final int prefixType = task.getPrefixType();
        final long prefixLo = task.getPrefixLo();
        final long prefixHi = task.getPrefixHi();
        final int suffixType = task.getSuffixType();
        final long suffixLo = task.getSuffixLo();
        final long suffixHi = task.getSuffixHi();
        final int mergeType = task.getMergeType();
        final long timestampMergeIndexAddr = task.getTimestampMergeIndexAddr();
        final SOUnboundedCountDownLatch doneLatch = task.getDoneLatch();

        subSeq.done(cursor);

        openColumn(
                configuration,
                outboundQueue,
                outboundPubSeq,
                openColumnMode,
                ff,
                pathToTable,
                partitionTimestamp,
                partitionBy,
                columnName,
                columnCounter,
                columnType,
                timestampMergeIndexAddr,
                srcOooFixAddr,
                srcOooFixSize,
                srcOooVarAddr,
                srcOooVarSize,
                srcOooLo,
                srcOooHi,
                srcDataMax,
                txn,
                prefixType,
                prefixLo,
                prefixHi,
                mergeType,
                mergeOOOLo,
                mergeOOOHi,
                mergeDataLo,
                mergeDataHi,
                mergeLen,
                suffixType,
                suffixLo,
                suffixHi,
                timestampFd,
                isIndexed,
                srcDataFixColumn,
                srcDataVarColumn,
                doneLatch
        );
    }
}