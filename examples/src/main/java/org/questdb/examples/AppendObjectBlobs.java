/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2017 Appsicle
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

package org.questdb.examples;

import com.questdb.std.ex.JournalException;
import com.questdb.store.JournalEntryWriter;
import com.questdb.store.JournalWriter;
import com.questdb.store.factory.Factory;
import com.questdb.store.factory.configuration.JournalConfiguration;
import com.questdb.store.factory.configuration.JournalConfigurationBuilder;
import com.questdb.store.factory.configuration.JournalStructure;

import java.io.*;
import java.util.zip.GZIPOutputStream;

public class AppendObjectBlobs {
    private static final byte[] buffer = new byte[1024 * 1024];

    public static void main(String[] args) throws JournalException, IOException {

        final String dirToIndex = args[1];

        JournalConfiguration configuration = new JournalConfigurationBuilder().build(args[0]);
        try (Factory factory = new Factory(configuration, 1000, 1, 0)) {

            try (JournalWriter writer = factory.writer(new JournalStructure("files") {{
                $sym("name").index();
                $bin("data");
                $ts();
            }})) {

                long t = System.currentTimeMillis();
                int count = processDir(writer, new File(dirToIndex));
                System.out.println("Added " + count + " files in " + (System.currentTimeMillis() - t) + " ms.");
            }
        }
    }

    private static int processDir(JournalWriter writer, File dir) throws JournalException, IOException {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {

                if (f.isDirectory()) {
                    count += processDir(writer, f);
                    continue;
                }

                JournalEntryWriter w = writer.entryWriter(System.currentTimeMillis());
                w.putSym(0, f.getAbsolutePath());

                try (InputStream in = new FileInputStream(f)) {
                    try (GZIPOutputStream out = new GZIPOutputStream(w.putBin(1))) {
                        pump(in, out);
                    }
                }

                w.append();
                count++;
            }
            writer.commit();
        }

        return count;
    }

    private static void pump(InputStream in, OutputStream out) throws IOException {
        int r;
        while ((r = in.read(buffer)) != -1) {
            out.write(buffer, 0, r);
        }
    }
}
