/*
 * Copyright (c) 2014-2015. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.lang.experimental;

import com.nfsdb.Journal;
import com.nfsdb.JournalWriter;
import com.nfsdb.Partition;
import com.nfsdb.collections.mmap.MapValues;
import com.nfsdb.collections.mmap.MultiMap;
import com.nfsdb.column.ColumnType;
import com.nfsdb.column.FixedColumn;
import com.nfsdb.column.SymbolTable;
import com.nfsdb.factory.JournalFactory;
import com.nfsdb.factory.configuration.ColumnMetadata;
import com.nfsdb.factory.configuration.JournalConfigurationBuilder;
import com.nfsdb.lang.cst.StatefulJournalSource;
import com.nfsdb.lang.cst.impl.join.SlaveResetOuterJoin;
import com.nfsdb.lang.cst.impl.jsrc.JournalSourceImpl;
import com.nfsdb.lang.cst.impl.jsrc.StatefulJournalSourceImpl;
import com.nfsdb.lang.cst.impl.ksrc.SingleKeySource;
import com.nfsdb.lang.cst.impl.psrc.JournalPartitionSource;
import com.nfsdb.lang.cst.impl.qry.GenericRecordSource;
import com.nfsdb.lang.cst.impl.qry.JournalRecord;
import com.nfsdb.lang.cst.impl.qry.JournalRecordSource;
import com.nfsdb.lang.cst.impl.qry.Record;
import com.nfsdb.lang.cst.impl.ref.MutableIntVariableSource;
import com.nfsdb.lang.cst.impl.ref.StringRef;
import com.nfsdb.lang.cst.impl.ref.SymbolXTabVariableSource;
import com.nfsdb.lang.cst.impl.rsrc.AllRowSource;
import com.nfsdb.lang.cst.impl.rsrc.KvIndexHeadRowSource;
import com.nfsdb.lang.cst.impl.rsrc.KvIndexTopRowSource;
import com.nfsdb.model.Album;
import com.nfsdb.model.Band;
import com.nfsdb.model.Quote;
import com.nfsdb.test.tools.JournalTestFactory;
import com.nfsdb.test.tools.TestData;
import com.nfsdb.test.tools.TestUtils;
import com.nfsdb.utils.Dates;
import com.nfsdb.utils.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;

public class CstTest {

    @ClassRule
    public static final JournalTestFactory factory = new JournalTestFactory(
            new JournalConfigurationBuilder() {{
                $(Quote.class)
                        .$sym("sym").index().valueCountHint(15)
                        .$sym("ex").index().valueCountHint(10)
                        .$str("mode")
                        .$ts()
                ;

                $(Band.class).recordCountHint(10000)
                        .$sym("name").index().valueCountHint(10000)
                        .$sym("type").index().valueCountHint(5)
                ;

                $(Album.class).recordCountHint(100000)
                        .$sym("band").index().valueCountHint(10000)
                        .$sym("name").index().valueCountHint(100000)
                        .$ts("releaseDate")
                ;
            }}.build(Files.makeTempDir())
    );

    @BeforeClass
    public static void setUp() throws Exception {
        TestData.appendQuoteData2(factory.writer(Quote.class, "quote"));
    }

    @Test
    public void testLookup() throws Exception {
        int c = 10000;
        String joinColumn = "sym";
        JournalWriter<Quote> w = factory.writer(Quote.class, "q1", c);
        TestUtils.generateQuoteData(w, c);
        w.close();

        Journal<Quote> master = factory.reader(Quote.class, "q1", c);
        Journal<Quote> slave = factory.reader(Quote.class, "q1", c);
        SymbolTable masterTab = master.getSymbolTable(joinColumn);


        //////////////////

        MutableIntVariableSource key = new MutableIntVariableSource();
        StringRef sym = new StringRef(joinColumn);
        JournalRecordSource src = new JournalSourceImpl(new JournalPartitionSource(slave, false), new KvIndexHeadRowSource(sym, new SingleKeySource(key), 1000, 0, null));
        SymbolTable slaveTab = src.getJournal().getSymbolTable(joinColumn);
        ////////////////////

        int map[] = new int[masterTab.size()];


        Partition last = null;
        FixedColumn col = null;
        int colIndex = -1;

        long t = System.nanoTime();
        for (int i = 0; i < 1; i++) {

            Arrays.fill(map, -1);

            for (JournalRecord d : new JournalSourceImpl(new JournalPartitionSource(master, false), new AllRowSource())) {

                if (last != d.partition) {
                    last = d.partition;
                    if (colIndex == -1) {
                        colIndex = d.partition.getJournal().getMetadata().getColumnIndex(joinColumn);
                    }
                    col = (FixedColumn) d.partition.getAbstractColumn(colIndex);
                }

                assert col != null;

                int masterKey = col.getInt(d.rowid);
                int slaveKey = map[masterKey];

                if (slaveKey == -1) {
                    slaveKey = slaveTab.getQuick(masterTab.value(masterKey));
                    map[masterKey] = slaveKey;
                }

                key.setValue(slaveKey);
                src.reset();
                int count = 0;
                for (JournalRecord di : src) {
                    count++;
                }
//                System.out.println(count);
            }
        }
        System.out.println(System.nanoTime() - t);
    }

    @Test
    public void testJoinN() throws Exception {

        int c = 10000;
        JournalWriter<Quote> w = factory.writer(Quote.class, "q2", c);
        TestUtils.generateQuoteData(w, c);
        w.close();

        Journal<Quote> master = factory.reader(Quote.class, "q2", c);
        Journal<Quote> slave = factory.reader(Quote.class, "q2", c);

        StringRef sym = new StringRef("sym");
        StatefulJournalSource m;
        GenericRecordSource src = new SlaveResetOuterJoin(
                m = new StatefulJournalSourceImpl(
                        new JournalSourceImpl(new JournalPartitionSource(master, false), new AllRowSource())
                )
                ,
                new JournalSourceImpl(new JournalPartitionSource(slave, false), new KvIndexHeadRowSource(sym, new SingleKeySource(new SymbolXTabVariableSource(m, "sym", "sym")), 1000, 0, null))
        );

        long count = 0;
        long t = 0;
        for (int i = -2; i < 20; i++) {
            if (i == 0) {
                t = System.nanoTime();
                count = 0;
            }
            src.reset();
            for (Record d : src) {
                count++;
            }
        }
        System.out.println(count);
        System.out.println(System.nanoTime() - t);

    }

    @Test
    public void testJoinHead() throws Exception {

        int c = 1000000;
        JournalWriter<Quote> w = factory.writer(Quote.class, "q", c);
        TestUtils.generateQuoteData(w, c);
        w.close();

        Journal<Quote> master = factory.reader(Quote.class, "q", c);
        Journal<Quote> slave = factory.reader(Quote.class, "q", c);

        StringRef sym = new StringRef("sym");
        StatefulJournalSource m;
        GenericRecordSource src = new SlaveResetOuterJoin(
                m = new StatefulJournalSourceImpl(
                        new JournalSourceImpl(new JournalPartitionSource(master, false), new AllRowSource())
                )
                ,
                new JournalSourceImpl(new JournalPartitionSource(slave, false), new KvIndexTopRowSource(sym, new SingleKeySource(new SymbolXTabVariableSource(m, "sym", "sym")), null))
        );

        long count = 0;
        long t = 0;
        for (int i = -20; i < 20; i++) {
            if (i == 0) {
                t = System.nanoTime();
                count = 0;
            }
            src.reset();
            for (Record d : src) {
                d.getDate(0);
                d.getDouble(2);
                d.getDouble(3);
                count++;
            }
        }
        System.out.println(count);
        System.out.println((System.nanoTime() - t) / 20);
    }

    public void testResamplingPerformance() throws Exception {

        JournalFactory factory = new JournalFactory("d:/data");
        Journal w = factory.reader("quote");

        int tsIndex = w.getMetadata().getColumnIndex("timestamp");
        int symIndex = w.getMetadata().getColumnIndex("sym");

        long t = 0;
        for (int i = -10; i < 10; i++) {
            if (i == 0) {
                t = System.nanoTime();
            }

            MultiMap map = new MultiMap.Builder()
                    .keyColumn(w.getMetadata().getColumnMetadata(tsIndex))
                    .keyColumn(w.getMetadata().getColumnMetadata(symIndex))
                    .valueColumn(new ColumnMetadata() {{
                        name = "count";
                        type = ColumnType.INT;
                    }})
                    .setCapacity(50)
                    .setDataSize(500 * 1024)
                    .setLoadFactor(0.5f)
                    .build();

            long prev = -1;
            for (JournalRecord e : w.rows()) {
                long ts = Dates.floorMI(e.getLong(tsIndex));

                if (ts != prev) {
                    map.clear();
                    prev = ts;
                }

                MapValues val = map.claimSlot(
                        map.claimKey()
                                .putLong(ts)
                                .putInt(e.getInt(symIndex))
                                .commit()
                );

                val.putInt(0, val.isNew() ? 1 : val.getInt(0) + 1);
            }
            System.out.println(map.size());

//        JournalEntryPrinter out = new JournalEntryPrinter(sink, true);
//        out.print(map.iterator());
            map.free();
        }
        System.out.println(System.nanoTime() - t);
    }

}
