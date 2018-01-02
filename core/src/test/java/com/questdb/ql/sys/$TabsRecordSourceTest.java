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

package com.questdb.ql.sys;

import com.questdb.parser.sql.AbstractOptimiserTest;
import com.questdb.std.time.DateFormatUtils;
import com.questdb.store.JournalEntryWriter;
import com.questdb.store.JournalWriter;
import org.junit.BeforeClass;
import org.junit.Test;

public class $TabsRecordSourceTest extends AbstractOptimiserTest {
    @BeforeClass
    public static void setUp() throws Exception {
        try (JournalWriter w = compiler.createWriter(FACTORY_CONTAINER.getFactory(), "create table xyz(x int, y string, timestamp date) timestamp(timestamp) partition by MONTH")) {
            JournalEntryWriter ew;

            ew = w.entryWriter(DateFormatUtils.parseDateTime("2016-01-02T00:00:00.000Z"));
            ew.putInt(0, 0);
            ew.append();

            ew = w.entryWriter(DateFormatUtils.parseDateTime("2016-02-02T00:00:00.000Z"));
            ew.putInt(0, 1);
            ew.append();

            w.commit();
        }
        $TabsRecordSource.init();
    }

    @Test
    public void testAsSubQuery() throws Exception {
        assertThat("1\n", "select count() from ($tabs order by last_modified desc)");
    }

    @Test
    public void testCompiled() throws Exception {
        assertThat("xyz\tMONTH\t2\t3\t9703504\n", "select name, partition_by, partition_count, column_count, size from $tabs");
    }

    @Test
    public void testInJoin() throws Exception {
        assertThat("xyz\txyz\n", "select a.name, b.name from $tabs a cross join $tabs b");
    }
}