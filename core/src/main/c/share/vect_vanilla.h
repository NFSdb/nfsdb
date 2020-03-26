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

#ifndef VECT_VANILLA_H
#define VECT_VANILLA_H

#include <climits>

double sumDouble_Vanilla(double *d, int64_t count);

double avgDouble_Vanilla(double *d, int64_t count);

double minDouble_Vanilla(double *d, int64_t count);

double maxDouble_Vanilla(double *d, int64_t count);

int64_t sumInt_Vanilla(int32_t *pi, int64_t count);

double avgInt_Vanilla(int32_t *pi, int64_t count);

int32_t minInt_Vanilla(int32_t *pi, int64_t count);

int32_t maxInt_Vanilla(int32_t *pi, int64_t count);

int64_t sumLong_Vanilla(int64_t *pl, int64_t count);

int64_t minLong_Vanilla(int64_t *pl, int64_t count);

int64_t maxLong_Vanilla(int64_t *pl, int64_t count);

double avgLong_Vanilla(int64_t *pl, int64_t count);

bool hasNull_Vanilla(int32_t *pi, int64_t count);

#endif //VECT_VANILLA_H
