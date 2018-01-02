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

package com.questdb.net.ha;

import com.questdb.model.Quote;
import com.questdb.std.time.DateFormatUtils;
import com.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JournalTest extends AbstractJournalTest {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestUtils.generateQuoteData(origin, 1000);
    }

    @Test
    public void testConsumerEqualToProducer() throws Exception {
        master.append(origin);
        master.commit(false, 101L, 10);
        slave.append(origin);
        slave.commit(false, 101L, 10);
        executeSequence(false);
    }

    @Test
    public void testConsumerLargerThanProducer() throws Exception {
        master.append(origin.query().all().asResultSet().subset(0, 550));
        master.commit(false, 101L, 10);
        slave.append(origin);
        slave.commit(false, 101L, 10);
        executeSequence(false);
    }

    @Test
    public void testConsumerPartitionEdge() throws Exception {
        origin.truncate();

        TestUtils.generateQuoteData(origin, 500, DateFormatUtils.parseDateTime("2013-10-01T00:00:00.000Z"));
        TestUtils.generateQuoteData(origin, 500, DateFormatUtils.parseDateTime("2013-11-01T00:00:00.000Z"));
        TestUtils.generateQuoteData(origin, 500, DateFormatUtils.parseDateTime("2013-12-01T00:00:00.000Z"));

        master.append(origin.query().all().asResultSet().subset(0, 500));
        master.commit(false, 101L, 10);
        master.append(origin.query().all().asResultSet().subset(500, 1500));
        master.commit(false, 102L, 20);
        slave.append(origin.query().all().asResultSet().subset(0, 500));
        slave.commit(false, 101L, 10);
        Assert.assertEquals(1, slave.getPartitionCount());
        executeSequence(true);
    }

    @Test
    public void testConsumerReset() throws Exception {
        master.append(origin.query().all().asResultSet().subset(0, 200));
        master.commit(false, 101L, 10);
        master.append(origin.query().all().asResultSet().subset(200, 550));
        master.commit(false, 102L, 20);
        slave.append(origin.query().all().asResultSet().subset(0, 200));
        slave.commit(false, 101L, 10);
        executeSequence(true);
        master.append(origin.query().all().asResultSet().subset(550, 1000));
        master.commit(false, 103L, 30);
        executeSequence(true);
    }

    @Test
    public void testConsumerSmallerThanProducer() throws Exception {
        master.append(origin.query().all().asResultSet().subset(0, 655));
        master.commit(false, 101L, 10);
        master.append(origin.query().all().asResultSet().subset(655, (int) origin.size()));
        master.commit(false, 102L, 20);

        slave.append(origin.query().all().asResultSet().subset(0, 655));
        slave.commit(false, 101L, 10);
        Assert.assertEquals(655, slave.size());
        executeSequence(true);
    }

    @Test
    public void testEmptyConsumerAndPopulatedProducer() throws Exception {
        master.append(origin);
        master.commit();
        executeSequence(true);
        Assert.assertEquals(1000, slave.size());
    }

    @Test
    public void testEmptyConsumerAndProducer() throws Exception {
        executeSequence(false);
    }

    @Test
    public void testEmptyPartitionAdd() throws Exception {
        master.append(origin);
        master.getAppendPartition(DateFormatUtils.parseDateTime("2013-12-01T00:00:00.000Z"));
        master.append(new Quote().setTimestamp(DateFormatUtils.parseDateTime("2014-01-01T00:00:00.000Z")));
        master.commit();
        executeSequence(true);
        Assert.assertEquals(master.getPartitionCount(), slave.getPartitionCount());
    }

    @Test
    public void testLagConsumerSmallerThanProducer() throws Exception {
        master.append(origin.query().all().asResultSet().subset(0, 350));
        master.mergeAppend(origin.query().all().asResultSet().subset(350, 600));
        master.commit();
        executeSequence(true);
    }

    @Test
    public void testLagReplace() throws Exception {
        master.append(origin.query().all().asResultSet().subset(0, 350));
        master.mergeAppend(origin.query().all().asResultSet().subset(350, 600));
        master.commit(false, 101L, 10);

        slave.append(origin.query().all().asResultSet().subset(0, 350));
        slave.mergeAppend(origin.query().all().asResultSet().subset(350, 600));
        slave.commit(false, 101L, 10);

        master.mergeAppend(origin.query().all().asResultSet().subset(600, 1000));
        master.commit(false, 102L, 20);
        executeSequence(true);
    }
}
