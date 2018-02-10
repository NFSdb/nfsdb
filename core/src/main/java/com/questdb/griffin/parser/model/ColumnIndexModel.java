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

package com.questdb.griffin.parser.model;

import com.questdb.griffin.common.ExprNode;
import com.questdb.std.Mutable;
import com.questdb.std.ObjectFactory;

public class ColumnIndexModel implements Mutable {
    public static final ObjectFactory<ColumnIndexModel> FACTORY = ColumnIndexModel::new;

    private ExprNode name;
    private int buckets;

    private ColumnIndexModel() {
    }

    @Override
    public void clear() {
        this.name = null;
        this.buckets = 0;
    }

    public int getBuckets() {
        return buckets;
    }

    public void setBuckets(int buckets) {
        this.buckets = buckets;
    }

    public ExprNode getName() {
        return name;
    }

    public void setName(ExprNode name) {
        this.name = name;
    }
}
