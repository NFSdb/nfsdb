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
import com.questdb.net.ha.comsumer.JournalClientStateConsumer;
import com.questdb.net.ha.comsumer.JournalDeltaConsumer;
import com.questdb.net.ha.model.IndexedJournal;
import com.questdb.net.ha.producer.JournalClientStateProducer;
import com.questdb.net.ha.producer.JournalDeltaProducer;
import com.questdb.std.ex.JournalException;
import com.questdb.std.ex.JournalNetworkException;
import com.questdb.store.Journal;
import com.questdb.store.JournalWriter;
import com.questdb.test.tools.AbstractTest;
import com.questdb.test.tools.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class AbstractJournalTest extends AbstractTest {

    JournalWriter<Quote> origin;
    JournalWriter<Quote> master;
    JournalWriter<Quote> slave;

    private MockByteChannel channel;

    private JournalDeltaProducer journalDeltaProducer;
    private JournalDeltaConsumer journalDeltaConsumer;
    private JournalClientStateProducer journalClientStateProducer;
    private JournalClientStateConsumer journalClientStateConsumer;
    private Journal<Quote> masterReader;

    @Before
    public void setUp() throws Exception {
        origin = getFactory().writer(Quote.class, "origin");
        slave = getFactory().writer(Quote.class, "slave");
        master = getFactory().writer(Quote.class, "master");

        journalClientStateProducer = new JournalClientStateProducer();
        journalClientStateConsumer = new JournalClientStateConsumer();

        this.masterReader = getFactory().reader(Quote.class, "master");
        journalDeltaProducer = new JournalDeltaProducer(masterReader);
        journalDeltaConsumer = new JournalDeltaConsumer(slave);
        channel = new MockByteChannel();
    }

    @After
    public void tearDown() throws Exception {
        origin.close();
        slave.close();
        master.close();
        masterReader.close();
        journalDeltaProducer.free();
        journalDeltaConsumer.free();
        super.tearDown();

    }

    void executeSequence(boolean expectContent) throws JournalNetworkException, JournalException {
        slave.refresh();
        journalClientStateProducer.write(channel, new IndexedJournal(0, slave));
        journalClientStateConsumer.read(channel);

        journalDeltaProducer.configure(journalClientStateConsumer.getValue().getTxn(), journalClientStateConsumer.getValue().getTxPin());
        Assert.assertEquals(expectContent, journalDeltaProducer.hasContent());
        if (expectContent) {
            journalDeltaProducer.write(channel);
            journalDeltaConsumer.read(channel);
            TestUtils.assertEquals(master, slave);
        }
    }
}
