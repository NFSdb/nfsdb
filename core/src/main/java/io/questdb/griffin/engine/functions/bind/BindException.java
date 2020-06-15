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

package io.questdb.griffin.engine.functions.bind;

import io.questdb.std.FlyweightMessageContainer;
import io.questdb.std.Sinkable;
import io.questdb.std.ThreadLocal;
import io.questdb.std.str.CharSink;
import io.questdb.std.str.StringSink;

public class BindException extends RuntimeException implements Sinkable, FlyweightMessageContainer {
    private static final ThreadLocal<BindException> tlException = new ThreadLocal<>(BindException::new);
    private final StringSink message = new StringSink();

    public static BindException $(CharSequence message) {
        return init().put(message);
    }

    public static BindException init() {
        BindException ex = tlException.get();
        ex.message.clear();
        return ex;
    }

    @Override
    public CharSequence getFlyweightMessage() {
        return message;
    }

    @Override
    public String getMessage() {
        return message.toString();
    }

    public BindException put(CharSequence cs) {
        message.put(cs);
        return this;
    }

    public BindException put(char c) {
        message.put(c);
        return this;
    }

    public BindException put(int value) {
        message.put(value);
        return this;
    }

    public BindException put(long value) {
        message.put(value);
        return this;
    }

    public BindException put(Sinkable sinkable) {
        message.put(sinkable);
        return this;
    }

    @Override
    public void toSink(CharSink sink) {
        sink.put(message);
    }
}
