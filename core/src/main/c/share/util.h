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

#ifndef UTIL_H
#define UTIL_H

#include <cmath>
#include <cstdint>
#include "jni.h"

#if (defined(__GNUC__) && !defined(__clang__))
#define ATTRIBUTE_NEVER_INLINE __attribute__((noinline))
#elif defined(_MSC_VER)
#define ATTRIBUTE_NEVER_INLINE __declspec(noinline)
#else
#define ATTRIBUTE_NEVER_INLINE
#endif

#define PREDICT_FALSE(x) (__builtin_expect(x, 0))
#define PREDICT_TRUE(x) (__builtin_expect(false || (x), true))

constexpr jdouble D_MAX = std::numeric_limits<jdouble>::infinity();
constexpr jdouble D_MIN = -std::numeric_limits<jdouble>::infinity();
constexpr jint I_MAX = std::numeric_limits<jint>::max();
constexpr jint I_MIN = std::numeric_limits<jint>::min();
constexpr jlong L_MIN = std::numeric_limits<jlong>::min();
constexpr jlong L_MAX = std::numeric_limits<jlong>::max();
constexpr jdouble D_NAN = std::numeric_limits<jdouble>::quiet_NaN();

inline uint32_t ceil_pow_2(uint32_t v) {
    v--;
    v |= v >> 1u;
    v |= v >> 2u;
    v |= v >> 4u;
    v |= v >> 8u;
    v |= v >> 16u;
    return v + 1;
}
// the "high" boundary is inclusive
template<class T, class V>
inline int64_t scan_search(T data, V value, int64_t low, int64_t high, int32_t scan_dir) {
    for (int64_t p = low; p <= high; p++) {
        if (data[p] == value) {
            p += scan_dir;
            while (p > 0 && p <= high && data[p] == value) {
                p += scan_dir;
            }
            return p - scan_dir;
        }
        if (data[p] > value) {
            return -p - 1;
        }
    }
    return -(high + 1) - 1;
}

// the "high" boundary is inclusive
template<class T, class V>
inline int64_t binary_search(T *data, V value, int64_t low, int64_t high, int32_t scan_dir) {
    while (low < high) {
        if (high - low < 65) {
            return scan_search(data, value, low, high, scan_dir);
        }
        int64_t mid = (low + high) / 2;
        T midVal = data[mid];

        if (midVal < value) {
            if (low < mid) {
                low = mid;
            } else {
                if (data[high] > value) {
                    return -low - 1;
                }
                return -high - 1;
            }
        } else if (midVal > value)
            high = mid;
        else {
            // In case of multiple equal values, find the first
            mid += scan_dir;
            while (mid > 0 && mid <= high && data[mid] == midVal) {
                mid += scan_dir;
            }
            return mid - scan_dir;
        }
    }

    if (data[low] > value) {
        return -low - 1;
    }

    if (data[low] == value) {
        return low;
    }

    return -(low + 1) - 1;
}

#endif //UTIL_H
