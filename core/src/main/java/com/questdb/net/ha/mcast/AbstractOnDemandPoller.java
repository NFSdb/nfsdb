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

package com.questdb.net.ha.mcast;

import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.net.ha.config.ClientConfig;
import com.questdb.net.ha.config.DatagramChannelWrapper;
import com.questdb.std.ByteBuffers;
import com.questdb.std.ex.JournalNetworkException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public abstract class AbstractOnDemandPoller<T> {
    private static final Log LOG = LogFactory.getLog(AbstractOnDemandPoller.class);
    private final ClientConfig networkConfig;
    private final int inMessageCode;
    private final int outMessageCode;

    AbstractOnDemandPoller(ClientConfig networkConfig, int inMessageCode, int outMessageCode) {
        this.networkConfig = networkConfig;
        this.inMessageCode = inMessageCode;
        this.outMessageCode = outMessageCode;
    }

    public T poll(int retryCount, long timeout, TimeUnit timeUnit) throws JournalNetworkException {
        try (DatagramChannelWrapper dcw = networkConfig.openDatagramChannel()) {
            DatagramChannel dc = dcw.getChannel();
            LOG.info().$("Polling on").$(dcw.getGroup()).$(" [").$(dc.getOption(StandardSocketOptions.IP_MULTICAST_IF).getName()).$(']').$();

            Selector selector = Selector.open();
            dc.configureBlocking(false);
            dc.register(selector, SelectionKey.OP_READ);
            // print out each datagram that we receive
            ByteBuffer buf = ByteBuffer.allocateDirect(4096);
            try {
                int count = retryCount;
                InetSocketAddress sa = null;
                while (count > 0 && (sa = poll0(dc, dcw.getGroup(), selector, buf, timeUnit.toMillis(timeout))) == null) {
                    buf.clear();
                    count--;
                }

                if (count == 0) {
                    throw new JournalNetworkException("Cannot find QuestDB servers on network");
                }

                return transform(buf, sa);
            } finally {
                ByteBuffers.release(buf);
            }
        } catch (IOException e) {
            throw new JournalNetworkException(e);
        }
    }

    private InetSocketAddress poll0(DatagramChannel dc, SocketAddress group, Selector selector, ByteBuffer buf, long timeoutMillis) throws IOException {
        while (true) {
            buf.putInt(outMessageCode);
            buf.flip();
            dc.send(buf, group);

            int count = 2;
            while (count-- > 0) {
                int updated = selector.select(timeoutMillis);
                if (updated == 0) {
                    return null;
                }
                if (updated > 0) {
                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        SelectionKey sk = iter.next();
                        iter.remove();
                        DatagramChannel ch = (DatagramChannel) sk.channel();
                        buf.clear();
                        InetSocketAddress sa = (InetSocketAddress) ch.receive(buf);
                        if (sa != null) {
                            buf.flip();
                            if (buf.remaining() >= 4 && inMessageCode == buf.getInt()) {
                                LOG.info().$("Receiving server information from: ").$(sa).$();
                                return sa;
                            }
                        }
                    }
                }
            }
        }
    }

    protected abstract T transform(ByteBuffer buf, InetSocketAddress sa);
}
