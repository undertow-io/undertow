/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

package io.undertow.conduits;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;

/**
 * Test if there is no overflow of state in chunk handling.
 *
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class ChunkSizeTestCase {
    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(ResponseCodeHandler.HANDLE_200);
    }

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

    @After
    public void after() throws Exception {
        IoUtils.safeClose(client);
    }

    @Test
    public void testForgedSmuggling() throws IOException, InterruptedException {

        final String msg = "POST /hello-servlet/greeting HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: appication/x-www-form-urlencoded\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n"
                + "4000000000000000\r\n";

        clientOutputStream.write(msg.getBytes());
        clientOutputStream.flush();
        Thread.currentThread().sleep(300);
        final String msg2 = "\r\nGET /smuggled HTTP/1.1\r\n"
                + "Host: 127.0.0.1:8080\r\n"
                + "Connection: close"
                + "\r\n\r\n";
        clientOutputStream.write(msg2.getBytes());
        clientOutputStream.flush();
        Thread.currentThread().sleep(3000);
        byte[] x = readAvailable();
        //if we fail, we will get two responses - 200 and 400 in this setup.
        Assert.assertEquals(new String(x), 0, x.length);

    }

    public byte[] readAvailable() throws IOException {
        byte[] buf = new byte[4096];
        try {
            int read = clientInputStream.read(buf);
            if (read <= 0) return new byte[0];
            byte[] result = new byte[read];
            System.arraycopy(buf, 0, result, 0, read);
            return result;
        } catch (SocketException expected) {
            return new byte[0];
        }
    }

}
