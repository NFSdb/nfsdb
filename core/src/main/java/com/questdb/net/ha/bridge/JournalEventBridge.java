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

package com.questdb.net.ha.bridge;

import com.questdb.mp.*;

import java.util.concurrent.TimeUnit;

public class JournalEventBridge {

    private static final int BUFFER_SIZE = 1024;
    private final RingQueue<JournalEvent> queue;
    private final Sequence publisher;
    private final FanOut fanOut;
    private final long time;
    private final TimeUnit unit;

    public JournalEventBridge(long time, TimeUnit unit) {
        this.queue = new RingQueue<>(JournalEvent.EVENT_FACTORY, BUFFER_SIZE);
        this.publisher = new MPSequence(BUFFER_SIZE);
        this.fanOut = new FanOut();
        this.publisher.then(fanOut).then(publisher);
        this.time = time;
        this.unit = unit;
    }

    public Sequence createAgentSequence() {
        return fanOut.addAndGet(new SCSequence(publisher.current(), new TimeoutBlockingWaitStrategy(time, unit)));
    }

    public RingQueue<JournalEvent> getQueue() {
        return queue;
    }

    public void publish(final int journalIndex, final long timestamp) {
        long cursor = publisher.nextBully();
        JournalEvent event = queue.get(cursor);
        event.setIndex(journalIndex);
        event.setTimestamp(timestamp);
        publisher.done(cursor);
    }

    public void removeAgentSequence(Sequence sequence) {
        fanOut.remove(sequence);
    }
}
