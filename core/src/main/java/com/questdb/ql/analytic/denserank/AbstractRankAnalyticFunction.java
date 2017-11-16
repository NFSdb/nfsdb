/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2017 Appsicle
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

package com.questdb.ql.analytic.denserank;

import com.questdb.common.ColumnType;
import com.questdb.common.Record;
import com.questdb.common.RecordColumnMetadata;
import com.questdb.common.RecordCursor;
import com.questdb.ql.RecordColumnMetadataImpl;
import com.questdb.ql.analytic.AnalyticFunction;

public abstract class AbstractRankAnalyticFunction implements AnalyticFunction {

    private final RecordColumnMetadata metadata;
    protected long rank = -1;

    public AbstractRankAnalyticFunction(String name) {
        this.metadata = new RecordColumnMetadataImpl(name, ColumnType.LONG);
    }

    @Override
    public void add(Record record) {
    }

    @Override
    public long getLong() {
        return rank;
    }

    @Override
    public RecordColumnMetadata getMetadata() {
        return metadata;
    }

    @Override
    public int getType() {
        return AnalyticFunction.STREAM;
    }

    @Override
    public void prepare(RecordCursor cursor) {
    }
}
