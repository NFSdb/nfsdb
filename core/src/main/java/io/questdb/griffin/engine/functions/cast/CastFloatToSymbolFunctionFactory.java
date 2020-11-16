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

package io.questdb.griffin.engine.functions.cast;

import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.TableUtils;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.SymbolTable;
import io.questdb.cairo.sql.SymbolTableSource;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.engine.functions.SymbolFunction;
import io.questdb.griffin.engine.functions.UnaryFunction;
import io.questdb.griffin.engine.functions.constants.SymbolConstant;
import io.questdb.std.Chars;
import io.questdb.std.IntIntHashMap;
import io.questdb.std.Misc;
import io.questdb.std.ObjList;
import io.questdb.std.str.StringSink;

public class CastFloatToSymbolFunctionFactory implements FunctionFactory {
    @Override
    public String getSignature() {
        return "cast(Fk)";
    }

    @Override
    public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration, SqlExecutionContext sqlExecutionContext) {
        final Function arg = args.getQuick(0);
        if (arg.isConstant()) {
            final StringSink sink = Misc.getThreadLocalBuilder();
            sink.put(arg.getFloat(null), 4);
            return new SymbolConstant(position, Chars.toString(sink), 0);
        }
        return new Func(position, arg);
    }

    private static class Func extends SymbolFunction implements UnaryFunction {
        private final Function arg;
        private final StringSink sink = new StringSink();
        private final IntIntHashMap symbolTableShortcut = new IntIntHashMap();
        private final ObjList<String> symbols = new ObjList<>();
        private int next = 1;

        public Func(int position, Function arg) {
            super(position);
            this.arg = arg;
            symbols.add(null);
        }

        @Override
        public Function getArg() {
            return arg;
        }

        @Override
        public CharSequence getSymbol(Record rec) {
            final float value = arg.getFloat(rec);
            if (Float.isNaN(value)) {
                return null;
            }

            final int key = Float.floatToIntBits(value);
            final int keyIndex = symbolTableShortcut.keyIndex(key);
            if (keyIndex < 0) {
                return symbols.getQuick(symbolTableShortcut.valueAt(keyIndex));
            }

            symbolTableShortcut.putAt(keyIndex, key, next++);
            sink.clear();
            sink.put(value, 4);
            final String str = Chars.toString(sink);
            symbols.add(Chars.toString(sink));
            return str;
        }

        @Override
        public CharSequence valueOf(int symbolKey) {
            return symbols.getQuick(TableUtils.toIndexKey(symbolKey));
        }

        @Override
        public int getInt(Record rec) {
            final float value = arg.getFloat(rec);
            if (Float.isNaN(value)) {
                return SymbolTable.VALUE_IS_NULL;
            }

            final int key = Float.floatToIntBits(value);
            final int keyIndex = symbolTableShortcut.keyIndex(key);
            if (keyIndex < 0) {
                return symbolTableShortcut.valueAt(keyIndex) - 1;
            }

            symbolTableShortcut.putAt(keyIndex, key, next);
            sink.clear();
            sink.put(value, 4);
            symbols.add(Chars.toString(sink));
            return next++ - 1;
        }

        @Override
        public boolean isSymbolTableStatic() {
            return false;
        }

        @Override
        public void init(SymbolTableSource symbolTableSource, SqlExecutionContext executionContext) {
            arg.init(symbolTableSource, executionContext);
            symbolTableShortcut.clear();
            symbols.clear();
            symbols.add(null);
            next = 1;
        }
    }
}
