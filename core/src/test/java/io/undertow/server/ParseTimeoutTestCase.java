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

package io.undertow.server;

import io.undertow.UndertowOptions;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class ParseTimeoutTestCase {

    private Socket client;
    private OutputStream clientOutputStream;
    private InputStream clientInputStream;

    @Before
    public void before() throws Exception {
        client = new Socket();
        client.connect(DefaultServer.getDefaultServerAddress());
        clientOutputStream = client.getOutputStream();
        clientInputStream = client.getInputStream();
    }

    public void after() throws Exception {
        IoUtils.safeClose(client);
        DefaultServer.setUndertowOptions(OptionMap.EMPTY);
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.REQUEST_PARSE_TIMEOUT, 10));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        DefaultServer.setUndertowOptions(OptionMap.EMPTY);
    }

    @Test(timeout = 10000)
    public void testClosingConnectionWhenParsingHeadersForTooLong() throws Exception {
        //given
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                fail("Parser should never end its job, since we are streaming headers.");
            }
        });

        String request = "GET / HTTP/1.1\r\nHost:localhost";

        //when
        clientOutputStream.write(request.getBytes());
        clientOutputStream.flush();

        Thread.sleep(100);

        //then
        assertEquals(-1, clientInputStream.read());
    }
}
