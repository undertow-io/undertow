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

import java.io.IOException;
import java.net.Socket;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;

import io.undertow.util.StatusCodes;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class BadRequestTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.setStatusCode(StatusCodes.OK);
            }
        });
    }

    /**
     * We send our request manually, as apache HTTP client does not support this.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testBadRequest() throws IOException {
        String request = "POST /\r HTTP/1.1\r\nTrailer:foo, bar\r\nTransfer-Encoding: chunked\r\n\r\n9\r\nabcdefghi\r\n0\r\nfoo: fooVal\r\n bar: barVal\r\n\r\n";
        String response1 = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
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
            Assert.assertEquals(response1, sb.toString());

        } catch (IOException expected) {
            //this can happen as well, as in some cases we may not have fully consumed the read side
            //before the connection is shutdown, namely when we are running in test.single
        } finally {
            s.close();
        }
    }

}
