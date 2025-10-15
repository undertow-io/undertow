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

package io.undertow.servlet.test.streams;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Tests the behaviour of the input stream when the connection is closed on the client side
 * <p>
 * https://issues.jboss.org/browse/WFLY-4827
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ServletInputStreamEarlyCloseClientSideTestCase {

    public static final String SERVLET = "servlet";

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo(SERVLET, EarlyCloseClientServlet.class)
                        .addMapping("/" + SERVLET));
    }

    @Test
    public void testServletInputStreamEarlyClose() throws Exception {
        Assume.assumeFalse(DefaultServer.isH2());
        TestHttpClient client = new TestHttpClient();
        EarlyCloseClientServlet.reset();
        try (Socket socket = new Socket()) {
            socket.connect(DefaultServer.getDefaultServerAddress());
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 10000; ++i) {
                    sb.append("hello world\r\n");
                }
                //send a large request that is too small, then kill the socket
                String request = "POST /servletContext/" + SERVLET + " HTTP/1.1\r\nHost:localhost\r\nContent-Length:" + sb.length() + 100 + "\r\n\r\n" + sb.toString();
                OutputStream outputStream = socket.getOutputStream();

                outputStream.write(request.getBytes("US-ASCII"));
                outputStream.flush();
                socket.close();

                Assert.assertTrue(EarlyCloseClientServlet.getLatch().await(10, TimeUnit.SECONDS));
                Assert.assertFalse(EarlyCloseClientServlet.isCompletedNormally());
                Assert.assertTrue(EarlyCloseClientServlet.isExceptionThrown());
            } finally {
                client.getConnectionManager().shutdown();
            }
        }
    }


}
