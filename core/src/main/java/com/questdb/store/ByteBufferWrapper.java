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

package com.questdb.store;

import com.questdb.std.ByteBuffers;

import java.io.Closeable;
import java.nio.MappedByteBuffer;

class ByteBufferWrapper implements Closeable {
    private final long offset;
    private MappedByteBuffer byteBuffer;

    public ByteBufferWrapper(long offset, MappedByteBuffer byteBuffer) {
        this.offset = offset;
        this.byteBuffer = byteBuffer;
    }

    @Override
    public void close() {
        byteBuffer = ByteBuffers.release(byteBuffer);
    }

    public MappedByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public long getOffset() {
        return offset;
    }
}
