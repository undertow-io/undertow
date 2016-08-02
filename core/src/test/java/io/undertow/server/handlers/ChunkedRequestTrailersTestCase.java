/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.server.protocol.http.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.StatusCodes;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class ChunkedRequestTrailersTestCase {

    private static volatile HttpServerConnection connection;

    private static OptionMap existing;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        existing = DefaultServer.getUndertowOptions();
        DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.ALWAYS_SET_DATE, false));
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if (connection == null) {
                        connection = (HttpServerConnection) exchange.getConnection();
                    } else if (!DefaultServer.isProxy() && connection != exchange.getConnection()) {
                        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        final OutputStream outputStream = exchange.getOutputStream();
                        outputStream.write("Connection not persistent".getBytes());
                        outputStream.close();
                        return;
                    }
                    final OutputStream outputStream = exchange.getOutputStream();
                    final InputStream inputStream = exchange.getInputStream();
                    String m = HttpClientUtils.readResponse(inputStream);
                    Assert.assertEquals("abcdefghi", m);

                    HeaderMap headers = exchange.getAttachment(HttpAttachments.REQUEST_TRAILERS);
                    for (HeaderValues header : headers) {
                        for (String val : header) {
                            outputStream.write(header.getHeaderName().toString().getBytes());
                            outputStream.write(": ".getBytes());
                            outputStream.write(val.getBytes());
                            outputStream.write("\r\n".getBytes());
                        }
                    }

                    inputStream.close();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @AfterClass
    public static void cleanup() {
        DefaultServer.setUndertowOptions(existing);
    }

    /**
     * We send our request manually, as apache HTTP client does not support this.
     *
     * @throws IOException
     */
    @Test
    public void testChunkedRequestsWithTrailers() throws IOException {
        connection = null;
        String request = "POST / HTTP/1.1\r\nHost: default\r\nTrailer:foo, bar\r\nTransfer-Encoding: chunked\r\n\r\n9\r\nabcdefghi\r\n0\r\nfoo: fooVal\r\n bar: barVal\r\n\r\n";
        String response1 = "HTTP/1.1 200 OK\r\nConnection: keep-alive\r\nContent-Length: 26\r\n\r\nfoo: fooVal\r\nbar: barVal\r\n"; //header order is not guaranteed, we really should be parsing this properly
        String response2 = "HTTP/1.1 200 OK\r\nConnection: keep-alive\r\nContent-Length: 26\r\n\r\nfoo: fooVal\r\nbar: barVal\r\n"; //TODO: parse the response properly, or better yet ues a client that supports trailers
        Socket s = new Socket(DefaultServer.getDefaultServerAddress().getAddress(), DefaultServer.getDefaultServerAddress().getPort());
        try {
            s.getOutputStream().write(request.getBytes());

            StringBuilder sb = new StringBuilder();
            int read = 0;
            byte[] buf = new byte[100];
            while (read < response1.length()) {
                int r = s.getInputStream().read(buf);
                if (r <= 0) break;
                if (r > 0) {
                    read += r;
                    sb.append(new String(buf, 0, r));
                }
            }
            try {
                //this is pretty yuck
                Assert.assertEquals(response1, sb.toString());
            } catch (AssertionError e) {
                Assert.assertEquals(response2, sb.toString());
            }

            s.getOutputStream().write(request.getBytes());

            sb = new StringBuilder();
            read = 0;
            buf = new byte[100];
            while (read < response1.length()) {
                int r = s.getInputStream().read(buf);
                if (r <= 0) break;
                if (r > 0) {
                    read += r;
                    sb.append(new String(buf, 0, r));
                }
            }
            try {
                Assert.assertEquals(response1, sb.toString());
            } catch (AssertionError e) {
                Assert.assertEquals(response2, sb.toString());
            }

        } finally {
            s.close();
        }
    }

}
