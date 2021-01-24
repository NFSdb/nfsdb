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

package io.questdb.griffin.model;

import io.questdb.cairo.sql.Function;
import io.questdb.std.Mutable;
import io.questdb.std.ObjectFactory;

public class RuntimePeriodIntrinsic implements Mutable {
    public static final ObjectFactory<RuntimePeriodIntrinsic> FACTORY = RuntimePeriodIntrinsic::new;
    private long staticValue;
    private Function dynamicValue;
    private boolean isLoDynamic;
    private long dynamicIncrement   ;

    @Override
    public void clear() {
    }

    public RuntimePeriodIntrinsic setLess(long lo, Function function, long adjustComparison) {
        staticValue = lo;
        isLoDynamic = false;
        dynamicValue = function;
        dynamicIncrement = adjustComparison;
        return this;
    }
}
