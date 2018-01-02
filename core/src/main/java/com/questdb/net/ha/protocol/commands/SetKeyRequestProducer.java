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

package com.questdb.net.ha.protocol.commands;

import com.questdb.net.ha.AbstractObjectProducer;
import com.questdb.net.ha.model.IndexedJournalKey;

import java.nio.ByteBuffer;

public class SetKeyRequestProducer extends AbstractObjectProducer<IndexedJournalKey> {

    @Override
    protected int getBufferSize(IndexedJournalKey value) {
        return value.getKey().getBufferSize() + 4;
    }

    @Override
    protected void write(IndexedJournalKey value, ByteBuffer buffer) {
        buffer.putInt(value.getIndex());
        value.getKey().write(buffer);
    }
}
