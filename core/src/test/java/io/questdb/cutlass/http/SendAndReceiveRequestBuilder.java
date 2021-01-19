/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
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

package io.questdb.cutlass.http;

import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.network.NetworkFacade;
import io.questdb.network.NetworkFacadeImpl;
import io.questdb.std.Chars;
import io.questdb.std.IntList;
import io.questdb.std.Unsafe;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;

public class SendAndReceiveRequestBuilder {
    public final static String RequestHeaders = "Host: localhost:9000\r\n" +
            "Connection: keep-alive\r\n" +
            "Accept: */*\r\n" +
            "X-Requested-With: XMLHttpRequest\r\n" +
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.87 Safari/537.36\r\n" +
            "Sec-Fetch-Site: same-origin\r\n" +
            "Sec-Fetch-Mode: cors\r\n" +
            "Referer: http://localhost:9000/index.html\r\n" +
            "Accept-Encoding: gzip, deflate, br\r\n" +
            "Accept-Language: en-GB,en-US;q=0.9,en;q=0.8\r\n" +
            "\r\n";
    public final static String ResponseHeaders =
            "HTTP/1.1 200 OK\r\n" +
                    "Server: questDB/1.0\r\n" +
                    "Date: Thu, 1 Jan 1970 00:00:00 GMT\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Keep-Alive: timeout=5, max=10000\r\n" +
                    "\r\n";

    private static final Log LOG = LogFactory.getLog(SendAndReceiveRequestBuilder.class);
    private NetworkFacade nf = NetworkFacadeImpl.INSTANCE;
    private long pauseBetweenSendAndReceive;
    private boolean printOnly;
    private boolean expectDisconnect;
    private int requestCount = 1;
    private int compareLength = -1;

    public void execute(
            String request,
            String response
    ) throws InterruptedException {
        final long fd = nf.socketTcp(true);
        nf.configureNoLinger(fd);
        try {
            long sockAddr = nf.sockaddr("127.0.0.1", 9001);
            try {
                Assert.assertTrue(fd > -1);
                long ret = nf.connect(fd, sockAddr);
                if (ret != 0) {
                    Assert.fail("could not connect: " + nf.errno());
                }
                Assert.assertEquals(0, nf.setTcpNoDelay(fd, true));

                executeWithSocket(request, response, fd, sockAddr);
            } finally {
                nf.freeSockAddr(sockAddr);
            }
        } finally {
            nf.close(fd);
        }
    }

    private void executeWithSocket(String request, String response, long fd, long sockAddr) throws InterruptedException {
        byte[] expectedResponse = response.getBytes();
        final int len = Math.max(expectedResponse.length, request.length()) * 2;
        long ptr = Unsafe.malloc(len);
        try {
            for (int j = 0; j < requestCount; j++) {
                executeExplicit(request, fd, expectedResponse, len, ptr, null);
            }
        } finally {
            Unsafe.free(ptr, len);
        }
    }

    public void executeExplicit(String request, long fd, byte[] expectedResponse, final int len, long ptr, HttpClientStateListener listener) throws InterruptedException {
        long timestamp = System.currentTimeMillis();
        int sent = 0;
        int reqLen = request.length();
        Chars.asciiStrCpy(request, reqLen, ptr);
        while (sent < reqLen) {
            int n = nf.send(fd, ptr + sent, reqLen - sent);
            Assert.assertTrue(n > -1);
            sent += n;
        }

        if (pauseBetweenSendAndReceive > 0) {
            Thread.sleep(pauseBetweenSendAndReceive);
        }
        // receive response
        final int expectedToReceive = expectedResponse.length;
        int received = 0;
        if (printOnly) {
            System.out.println("expected");
            System.out.println(new String(expectedResponse, StandardCharsets.UTF_8));
        }

        boolean disconnected = false;
        boolean timeoutExpired = false;
        IntList receivedByteList = new IntList(expectedToReceive);
        while (received < expectedToReceive) {
            int n = nf.recv(fd, ptr + received, len - received);
            if (n > 0) {
                // dump(ptr + received, n);
                // compare bytes
                for (int i = 0; i < n; i++) {
                    receivedByteList.add(Unsafe.getUnsafe().getByte(ptr + received + i));
                }
                received += n;
                if (null != listener) {
                    listener.onReceived(received);
                }
            } else if (n < 0) {
                disconnected = true;
                break;
            } else {
                int maxWaitTimeoutMs = 5000;
                if (System.currentTimeMillis() - timestamp > maxWaitTimeoutMs) {
                    timeoutExpired = true;
                    break;
                }
            }
        }
        byte[] receivedBytes = new byte[receivedByteList.size()];
        for (int i = 0; i < receivedByteList.size(); i++) {
            receivedBytes[i] = (byte) receivedByteList.getQuick(i);
        }

        String actual = new String(receivedBytes, StandardCharsets.UTF_8);
        if (!printOnly) {
            String expected = (new String(expectedResponse, StandardCharsets.UTF_8));
            if (compareLength > 0) {
                expected = expected.substring(0, Math.min(compareLength, expected.length()) - 1);
                actual = actual.length() > 0 ? actual.substring(0, Math.min(compareLength, actual.length()) - 1) : actual;
            }
            if (actual.length() == 0) {
                System.out.println("oopsie");
            }
            TestUtils.assertEquals(expected, actual);

        } else {
            System.out.println("actual");
            System.out.println(actual);
        }

        if (disconnected && !expectDisconnect) {
            LOG.error().$("disconnected?").$();
            Assert.fail();
        }

        if (timeoutExpired) {
            LOG.error().$("timeout expired").$();
            Assert.fail();
        }
    }

    public void executeWithStandardHeaders(
            String request,
            String response
    ) throws InterruptedException {
        execute(request + RequestHeaders, ResponseHeaders + response);
    }

    public void executeMany(RequestAction action) throws InterruptedException {
        final long fd = nf.socketTcp(true);
        nf.configureNoLinger(fd);
        try {
            long sockAddr = nf.sockaddr("127.0.0.1", 9001);
            Assert.assertTrue(fd > -1);
            long ret = nf.connect(fd, sockAddr);
            if (ret != 0) {
                Assert.fail("could not connect: " + nf.errno());
            }
            Assert.assertEquals(0, nf.setTcpNoDelay(fd, true));

            try {
                RequestExecutor executor = new RequestExecutor() {
                    @Override
                    public void executeWithStandardHeaders(String request, String response) throws InterruptedException {
                        executeWithSocket(request + RequestHeaders, ResponseHeaders + response, fd, sockAddr);
                    }

                    @Override
                    public void execute(String request, String response) throws InterruptedException {
                        executeWithSocket(request, response, fd, sockAddr);
                    }
                };

                action.run(executor);
            } finally {
                nf.freeSockAddr(sockAddr);
            }
        } finally {
            nf.close(fd);
        }
    }

    public SendAndReceiveRequestBuilder withCompareLength(int compareLength) {
        this.compareLength = compareLength;
        return this;
    }

    public SendAndReceiveRequestBuilder withExpectDisconnect(boolean expectDisconnect) {
        this.expectDisconnect = expectDisconnect;
        return this;
    }

    public SendAndReceiveRequestBuilder withNetworkFacade(NetworkFacade nf) {
        this.nf = nf;
        return this;
    }

    public SendAndReceiveRequestBuilder withPauseBetweenSendAndReceive(long pauseBetweenSendAndReceive) {
        this.pauseBetweenSendAndReceive = pauseBetweenSendAndReceive;
        return this;
    }

    public SendAndReceiveRequestBuilder withPrintOnly(boolean printOnly) {
        this.printOnly = printOnly;
        return this;
    }

    public SendAndReceiveRequestBuilder withRequestCount(int requestCount) {
        this.requestCount = requestCount;
        return this;
    }

    @FunctionalInterface
    public interface RequestAction {
        void run(RequestExecutor executor) throws InterruptedException;
    }

    public interface RequestExecutor {
        void executeWithStandardHeaders(
                String request,
                String response
        ) throws InterruptedException;

        void execute(
                String request,
                String response
        ) throws InterruptedException;
    }
}
