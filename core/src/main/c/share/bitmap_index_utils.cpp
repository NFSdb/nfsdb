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

#include <algorithm>
#include "bitmap_index_utils.h"

void latest_scan_backward(uint64_t keys_memory_addr, size_t keys_memory_size, uint64_t values_memory_addr,
                          size_t value_memory_size, uint64_t args_memory_addr, int64_t unindexed_null_count,
                          int64_t max_value, int64_t min_value, int32_t partition_index, uint32_t vblock_capacity_mask) {

    auto keys_memory = reinterpret_cast<const uint8_t *>(keys_memory_addr);
    auto values_memory = reinterpret_cast<const uint8_t *>(values_memory_addr);
    auto out_args = reinterpret_cast<out_arguments *>(args_memory_addr);

    keys_reader keys(keys_memory, keys_memory_size);

    auto key_count = keys.key_count();

    auto key_begin = out_args->key_lo;
    auto key_end = out_args->key_hi;

    auto rows = reinterpret_cast<int64_t*>(out_args->rows_address);

    const auto vblock_capacity = vblock_capacity_mask + 1;

    int64_t local_key_begin = std::numeric_limits<int64_t>::max();
    int64_t local_key_end = std::numeric_limits<int64_t>::min();

    auto row_count = 0;
    for(int64_t k = key_begin; k < key_end; ++k) {
        if(k > key_count) {
            if (k < local_key_begin) local_key_begin = k;
            if (k > local_key_end) local_key_end = k;
            continue;
        }

        if (rows[k] > 0) continue;

        auto key = keys[k];

        int64_t value_count = key.value_count;
        bool update_range = true;

        if(value_count > 0) {
            block<int64_t> tail(values_memory, key.last_value_block_offset, vblock_capacity);
            block<int64_t> inconsistent_tail(values_memory, key.first_value_block_offset, vblock_capacity);

            bool is_offset_in_mapped_area = tail.offset() + tail.memory_size() <= value_memory_size;
            bool is_inconsistent = !key.is_block_consistent || !is_offset_in_mapped_area;
            if (is_inconsistent) {
                // can trust only first block offset
                int64_t block_traversed = 1;
                while (inconsistent_tail.next_offset()
                       && inconsistent_tail.next_offset() + inconsistent_tail.memory_size() <= value_memory_size) {
                    inconsistent_tail.move_next();
                    block_traversed += 1;
                }
                //assuming blocks are full
                value_count = vblock_capacity * block_traversed;
            }

            auto current_block = is_inconsistent ? inconsistent_tail : tail;
            value_count = scan_blocks_backward<int64_t>(current_block, value_count, max_value);
            if (value_count > 0) {
                int64_t local_row_id = current_block[value_count - 1];
                if (local_row_id >= min_value) {
                    rows[k] = to_row_id(partition_index, local_row_id) + 1;
                    row_count += 1;
                    update_range = false;
                }
            }
        }
        // unindexed nulls case
        if (k == 0 && unindexed_null_count > 0) {
            if (rows[k] <= 0 && unindexed_null_count - 1 >= min_value) {
                rows[k] = to_row_id(partition_index, unindexed_null_count);
                row_count += 1;
                update_range = false;
            }
        }

        if (update_range) {
            if (k < local_key_begin) local_key_begin = k;
            if (k > local_key_end) local_key_end = k;
        }
    }

    out_args->key_lo = local_key_begin;
    out_args->key_hi = local_key_end;
    out_args->rows_size = row_count;
}

inline int64_t linked_search_lower(const int64_t *indexBase,
                            const int64_t *dataBase,
                            int64_t indexLength,
                            const int64_t estimatedCount,
                            const int64_t value) {
    // Timeseries data is usually distributed equally in time
    // This code optimises using assumption that as if there are 200 index elements within 20 mins
    // and sample by is by 1 minute the next value will be around every 10 elements
    // To not degrade it to full scan, use miniumum increment of 32
    // This is same as
    // return branch_free_linked_search_lower(indexBase, dataBase, indexLength, value);
    const auto step = std::max(32LL, indexLength / estimatedCount);
    int64_t searchStart = 0LL;
    while (searchStart + step < indexLength && dataBase[*(indexBase + searchStart + step - 1)] < value) {
        searchStart += step;
    }
    return searchStart + branch_free_linked_search_lower(indexBase + searchStart, dataBase,
                                                         std::min(step, indexLength - searchStart), value);
}

constexpr int64_t FL_FIRST_ROWID_OUT_OFFSET = 0;
constexpr int64_t FL_LAST_ROWID_OUT_OFFSET = 1;
constexpr int64_t FL_TIMESTAMP_INDEX_OUT_OFFSET = 2;
constexpr int64_t FL_ITEMS_PER_OUT_ARRAY = 4;

int32_t findFirstLastInFrame0(
        int32_t outIndex
        , const int64_t rowIdLo
        , const int64_t rowIdHi
        , const int64_t* tsBase
        , const int64_t frameBaseOffset
        , const int64_t* indexBase
        , const int64_t indexCount
        , const int64_t indexPosition
        , const int64_t* samplePeriods
        , const int32_t samplePeriodCount
        , const int64_t sampleIndexOffset
        , int64_t* outputRowIds
        , const int32_t outSize
        ) {
    // This method searches Timestamp column (tsBase, rowLo, rowHi)
    // for the first and last values in the sample windows set in samplePeriods
    // using index (indexBase, indexCount, indexPosition)
    // Index is symbol index, ascending Row Ids of the Timestamp column
    // Example:
    // when data is
    // 0         1         2         3         4         5         6         7         8         9         10
    // 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
    // ------------- | -------------------- | ---------------------- | ---------------------- | ------------------ |
    // a  b  c  a  b b  c a c b  a                                  ba     c a                   a           a
    //
    // periodStarts are noted as | at: 14, 37, 62, 87, 108
    // and indexes of 'a' are        : 0, 9, 19, 26, 62, 70, 90, 102
    // the search should find
    // outputRowIds(    first)       : 19, 62,  90
    // outputRowIds(     last)       : 26, 70, 102
    // outputRowIds(timestamp)       :  0,  2,   3
    // Output in timestampOut contains indexes of the periods found
    // Last value of output buffers reserved for positions to resume the search
    // outputRowIds[n + 0] :   7 ( index position                  )
    // outputRowIds[n + 1] :  108 ( timestamp column position       )
    // outputRowIds[n + 2] :    4 ( last processed period end index )
    int32_t periodIndex = 0;
    int64_t sampleStart;
    bool firstRowUpdated = false;

    const int64_t* indexLo = indexBase + indexPosition;
    const int64_t* indexHi = indexBase + indexCount;
    const int64_t* tsLo = tsBase + std::max(rowIdLo, *indexLo);
    const int64_t* tsHi = tsBase + rowIdHi;
    const int64_t maxTs = *(tsHi - 1);

    const int64_t maxOutLength = (outSize - 1) * FL_ITEMS_PER_OUT_ARRAY;
    outIndex *= FL_ITEMS_PER_OUT_ARRAY;

    while (indexLo < indexHi
           && periodIndex < samplePeriodCount
           && tsLo < tsHi
           && outIndex < maxOutLength) {

        sampleStart = samplePeriods[periodIndex];
        indexLo += linked_search_lower(indexLo, tsBase, indexHi - indexLo, samplePeriodCount - periodIndex,
                                       std::min(maxTs + 1, sampleStart));

        // Set last value as previous value to the found one
        if (outIndex > 0
            && outputRowIds[outIndex - FL_ITEMS_PER_OUT_ARRAY + FL_TIMESTAMP_INDEX_OUT_OFFSET] == periodIndex + sampleIndexOffset - 1 // prev out row is for period - 1
            && indexLo - indexBase > 0
                ) {
            int64_t prevLastRowId = *(indexLo - 1);
            outputRowIds[outIndex - FL_ITEMS_PER_OUT_ARRAY + FL_LAST_ROWID_OUT_OFFSET] = prevLastRowId - frameBaseOffset;
            firstRowUpdated |= outIndex == FL_ITEMS_PER_OUT_ARRAY;
        }

        if (indexLo == indexHi || sampleStart > maxTs || periodIndex > samplePeriodCount - 2) {
            break;
        }

        int64_t indexTs = tsBase[*indexLo];
        int64_t sampleEnd = (periodIndex + 1) < samplePeriodCount ? samplePeriods[periodIndex + 1] :
                            std::numeric_limits<int64_t>::max();
        if (indexTs >= sampleEnd) {
            // indexTs is beyond sampling period. Find the sampling period the indexTs is in.
            // branch_free_search_lower returns insert position of the value
            // We need the index of the first value less or equal to indexTs
            // This is the same as searching of (indexTs + 1) and getting previous index
            periodIndex += (int32_t) branch_free_search_lower(samplePeriods + periodIndex,
                                                              samplePeriodCount - periodIndex,
                                                              indexTs + 1) - 1;
            continue;
        }

        // Point next timestamp column position to found Index value
        tsLo = tsBase + *indexLo;
        if (tsLo >= tsHi || *tsLo < sampleStart) {
            // If index value is beyond data frame limits
            // or no symbol exists higher than the searched sampleStart
            // abort the search and return results
            break;
        }
        outputRowIds[outIndex + FL_LAST_ROWID_OUT_OFFSET] = outputRowIds[outIndex + FL_FIRST_ROWID_OUT_OFFSET]
                = *indexLo - frameBaseOffset;
        outputRowIds[outIndex + FL_TIMESTAMP_INDEX_OUT_OFFSET] = sampleIndexOffset + periodIndex;
        outIndex += FL_ITEMS_PER_OUT_ARRAY;
        periodIndex++;
    }

    // Save additional values in out buffers Java expects to find
    // Next indexPosition
    outputRowIds[outIndex + FL_FIRST_ROWID_OUT_OFFSET] = indexLo - indexBase;
    // Next rowIdLo
    outputRowIds[outIndex + FL_LAST_ROWID_OUT_OFFSET] = tsLo - tsBase;
    // Next timestamp to start from
    outputRowIds[outIndex + FL_TIMESTAMP_INDEX_OUT_OFFSET] = sampleIndexOffset + std::min(periodIndex, samplePeriodCount - 1);
    outIndex /= FL_ITEMS_PER_OUT_ARRAY;
    return firstRowUpdated ? -outIndex : outIndex;
}

int32_t findFirstLastInFrameNoFilter0(
        int32_t outIndex
        , const int64_t rowIdLo
        , const int64_t rowIdHi
        , const int64_t* tsBase
        , const int64_t frameBaseOffset
        , const int64_t* samplePeriods
        , const int32_t samplePeriodCount
        , const int64_t sampleIndexOffset
        , int64_t* outputRowIds
        , const int32_t outSize
) {
    // This method is the same as findFirstLastInFrame0
    // except it does not use index
    // All rows in tsBase from rowILo toRowIdHi are considered to be in the index

    // SampleBy period index
    int32_t periodIndex = 0;
    bool firstRowUpdated = false;

    const int64_t* tsLo = tsBase + rowIdLo;
    const int64_t* tsStart = tsLo;
    const int64_t* tsHi = tsBase + rowIdHi;
    const int64_t maxTs = *(tsHi - 1);

    outIndex *= FL_ITEMS_PER_OUT_ARRAY;
    const int64_t maxOutLength = (outSize - 1) * FL_ITEMS_PER_OUT_ARRAY;

    while (periodIndex < samplePeriodCount
           && tsLo < tsHi
           && outIndex < maxOutLength) {

        int64_t sampleStart = samplePeriods[periodIndex];
        tsLo += branch_free_search_lower(tsLo, tsHi - tsLo,
                                                   std::min(maxTs + 1, sampleStart));

        // Set last value as previous value to the found one
        if (outIndex > 0
            && outputRowIds[outIndex - FL_ITEMS_PER_OUT_ARRAY + FL_TIMESTAMP_INDEX_OUT_OFFSET] == periodIndex + sampleIndexOffset - 1 // prev out row is for period - 1
            && tsLo > tsStart
            ) {
            int64_t prevLastRowId = tsLo - tsBase - 1;
            outputRowIds[outIndex - FL_ITEMS_PER_OUT_ARRAY + FL_LAST_ROWID_OUT_OFFSET] = prevLastRowId - frameBaseOffset;
            firstRowUpdated |= outIndex == FL_ITEMS_PER_OUT_ARRAY;
        }

        if (sampleStart > maxTs || periodIndex > samplePeriodCount - 2) {
            break;
        }

        int64_t sampleEnd = samplePeriods[periodIndex + 1];
        if (*tsLo >= sampleEnd) {
            // indexTs is beyond sampling period. Find the sampling period the indexTs is in.
            // branch_free_search_lower returns insert position of the value
            // We need the index of the first value less or equal to indexTs
            // This is the same as searching of (indexTs + 1) and getting previous index
            periodIndex += (int32_t)branch_free_search_lower(samplePeriods + periodIndex,
                                                             samplePeriodCount - periodIndex,
                                                             *tsLo + 1) - 1;
            continue;
        }

        // Point next timestamp column position to found Index value
        if (tsLo >= tsHi || *tsLo < sampleStart) {
            // If index value is beyond data frame limits
            // or no symbol exists higher than the searched sampleStart
            // abort the search and return results
            break;
        }
        outputRowIds[outIndex + FL_LAST_ROWID_OUT_OFFSET] = outputRowIds[outIndex + FL_FIRST_ROWID_OUT_OFFSET]
                = (tsLo - tsBase) - frameBaseOffset;
        outputRowIds[outIndex + FL_TIMESTAMP_INDEX_OUT_OFFSET] = sampleIndexOffset + periodIndex;
        outIndex += FL_ITEMS_PER_OUT_ARRAY;
        periodIndex++;
    }

    // Save additional values in out buffers Java expects to find
    // Next timestamp to start from
    outputRowIds[outIndex + FL_TIMESTAMP_INDEX_OUT_OFFSET] = sampleIndexOffset + std::min(periodIndex, samplePeriodCount - 1);
    // Next rowIdLo
    outputRowIds[outIndex + FL_FIRST_ROWID_OUT_OFFSET] = outputRowIds[outIndex + FL_LAST_ROWID_OUT_OFFSET] = tsLo - tsBase + 1;
    outIndex /= FL_ITEMS_PER_OUT_ARRAY;
    return firstRowUpdated ? -outIndex : outIndex;
}

extern "C" {

JNIEXPORT void JNICALL
Java_io_questdb_std_BitmapIndexUtilsNative_latestScanBackward0(JNIEnv *env, jclass cl
                                                , jlong keysMemory
                                                , jlong keysMemorySize
                                                , jlong valuesMemory
                                                , jlong valuesMemorySize
                                                , jlong argsMemory
                                                , jlong unIndexedNullCount
                                                , jlong maxValue
                                                , jlong minValue
                                                , jint partitionIndex
                                                , jint blockValueCountMod) {
    latest_scan_backward(keysMemory, keysMemorySize, valuesMemory, valuesMemorySize, argsMemory, unIndexedNullCount,
                         maxValue, minValue, partitionIndex, blockValueCountMod);
}

JNIEXPORT jint JNICALL
Java_io_questdb_std_BitmapIndexUtilsNative_findFirstLastInFrame0(JNIEnv *env, jclass cl
        , jint outIndex
        , jlong rowIdLo
        , jlong rowIdHi
        , jlong timestampColAddress
        , jlong frameBaseOffset
        , jlong symbolIndexAddress
        , jlong symbolIndexCount
        , jlong symbolIndexPosition
        , jlong samplePeriodsAddress
        , jint samplePeriodCount
        , jlong samplePeriodIndexOffset
        , jlong firstRowIdOutAddress
        , jint outSize) {
    return findFirstLastInFrame0(
            outIndex
            , rowIdLo
            , rowIdHi
            , reinterpret_cast<int64_t *>(timestampColAddress)
            , frameBaseOffset
            , reinterpret_cast<int64_t *>(symbolIndexAddress)
            , symbolIndexCount
            , symbolIndexPosition
            , reinterpret_cast<int64_t *>(samplePeriodsAddress)
            , samplePeriodCount
            , samplePeriodIndexOffset
            , reinterpret_cast<int64_t *>(firstRowIdOutAddress)
            , outSize
    );
}

JNIEXPORT jint JNICALL
Java_io_questdb_std_BitmapIndexUtilsNative_findFirstLastInFrameNoFilter0(JNIEnv *env, jclass cl
        , jint outIndex
        , jlong rowIdLo
        , jlong rowIdHi
        , jlong timestampColAddress
        , jlong frameBaseOffset
        , jlong samplePeriodsAddress
        , jint samplePeriodCount
        , jlong samplePeriodIndexOffset
        , jlong firstRowIdOutAddress
        , jint outSize) {
    return findFirstLastInFrameNoFilter0(
            outIndex
            , rowIdLo
            , rowIdHi
            , reinterpret_cast<int64_t *>(timestampColAddress)
            , frameBaseOffset
            , reinterpret_cast<int64_t *>(samplePeriodsAddress)
            , samplePeriodCount
            , samplePeriodIndexOffset
            , reinterpret_cast<int64_t *>(firstRowIdOutAddress)
            , outSize
    );
}
} // extern "C"