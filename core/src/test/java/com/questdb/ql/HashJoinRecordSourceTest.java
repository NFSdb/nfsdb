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

package com.questdb.ql;

import com.questdb.model.Album;
import com.questdb.model.Band;
import com.questdb.ql.join.HashJoinRecordSource;
import com.questdb.ql.map.RecordKeyCopierCompiler;
import com.questdb.ql.select.SelectedColumnsRecordSource;
import com.questdb.std.BytecodeAssembler;
import com.questdb.std.IntList;
import com.questdb.std.ObjList;
import com.questdb.std.str.StringSink;
import com.questdb.store.JournalWriter;
import com.questdb.store.factory.Factory;
import com.questdb.store.factory.configuration.JournalConfigurationBuilder;
import com.questdb.test.tools.FactoryContainer;
import org.junit.*;

public class HashJoinRecordSourceTest {
    @Rule
    public final FactoryContainer factoryContainer = new FactoryContainer(new JournalConfigurationBuilder() {{
        $(Band.class).$ts();
        $(Album.class).$ts("releaseDate");

    }});

    private JournalWriter<Band> bw;
    private JournalWriter<Album> aw;

    @Before
    public void setUp() throws Exception {
        bw = getFactory().writer(Band.class);
        aw = getFactory().writer(Album.class);
    }

    @After
    public void tearDown() {
        bw.close();
        aw.close();

        Assert.assertEquals(0, getFactory().getBusyWriterCount());
        Assert.assertEquals(0, getFactory().getBusyReaderCount());
    }

    @Test
    public void testHashJoinJournalRecordSource() throws Exception {
        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("blues").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));
        bw.append(new Band().setName("band1").setType("jazz").setUrl("http://new.band1.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));

        aw.commit();

        StringSink sink = new StringSink();
        RecordSourcePrinter p = new RecordSourcePrinter(sink);
        RecordSource joinResult = new SelectedColumnsRecordSource(
                new HashJoinRecordSource(
                        new JournalRecordSource(new JournalPartitionSource(bw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(bw.getMetadata().getColumnIndex("name"));
                        }},
                        new JournalRecordSource(new JournalPartitionSource(aw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(aw.getMetadata().getColumnIndex("band"));
                        }},
                        false,
                        4 * 1024 * 1024,
                        4 * 1024 * 1024,
                        1024 * 1024,
                        new RecordKeyCopierCompiler(new BytecodeAssembler())
                ),
                new ObjList<CharSequence>() {{
                    add("genre");
                }}
        );
        p.print(joinResult, getFactory());
        Assert.assertEquals("pop\n" +
                "rock\n" +
                "metal\n" +
                "pop\n" +
                "rock\n", sink.toString());
    }

    @Test
    public void testHashJoinRecordSource() throws Exception {
        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("blues").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));
        bw.append(new Band().setName("band1").setType("jazz").setUrl("http://new.band1.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));

        aw.commit();

        StringSink sink = new StringSink();
        RecordSourcePrinter p = new RecordSourcePrinter(sink);
        RecordSource joinResult = new SelectedColumnsRecordSource(
                new HashJoinRecordSource(
                        new JournalRecordSource(new JournalPartitionSource(bw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(bw.getMetadata().getColumnIndex("name"));
                        }},
                        new JournalRecordSource(new JournalPartitionSource(aw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(aw.getMetadata().getColumnIndex("band"));
                        }},
                        false,
                        4 * 1024 * 1024,
                        4 * 1024 * 1024,
                        1024 * 1024,
                        new RecordKeyCopierCompiler(new BytecodeAssembler())
                ),
                new ObjList<CharSequence>() {{
                    add("genre");
                }}
        );
        p.print(joinResult, getFactory());
        Assert.assertEquals("pop\n" +
                "rock\n" +
                "metal\n" +
                "pop\n" +
                "rock\n", sink.toString());
    }

    @Test
    public void testOuterHashJoin() throws Exception {
        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("blues").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));
        bw.append(new Band().setName("band1").setType("jazz").setUrl("http://new.band1.com"));
        bw.append(new Band().setName("band5").setType("jazz").setUrl("http://new.band5.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));

        aw.commit();

        StringSink sink = new StringSink();
        RecordSourcePrinter p = new RecordSourcePrinter(sink);
        RecordSource joinResult = new SelectedColumnsRecordSource(
                new HashJoinRecordSource(
                        new JournalRecordSource(new JournalPartitionSource(bw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(bw.getMetadata().getColumnIndex("name"));
                        }},
                        new JournalRecordSource(new JournalPartitionSource(aw.getMetadata(), false), new AllRowSource()),
                        new IntList() {{
                            add(aw.getMetadata().getColumnIndex("band"));
                        }},
                        true,
                        4 * 1024 * 1024,
                        4 * 1024 * 1024,
                        1024 * 1024,
                        new RecordKeyCopierCompiler(new BytecodeAssembler())
                ),
                new ObjList<CharSequence>() {{
                    add("genre");
                    add("url");
                }}
        );
        p.print(joinResult, getFactory());
        Assert.assertEquals("pop\thttp://band1.com\n" +
                "rock\thttp://band1.com\n" +
                "\thttp://band2.com\n" +
                "metal\thttp://band3.com\n" +
                "pop\thttp://new.band1.com\n" +
                "rock\thttp://new.band1.com\n" +
                "\thttp://new.band5.com\n", sink.toString());
    }

    private Factory getFactory() {
        return factoryContainer.getFactory();
    }
}
