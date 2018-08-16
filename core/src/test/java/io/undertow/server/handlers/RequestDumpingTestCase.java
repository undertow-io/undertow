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
import java.nio.ByteBuffer;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.DateUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Farid Zakaria
 */
@RunWith(DefaultServer.class)
public class RequestDumpingTestCase {

    private static class EchoHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.getRequestReceiver().receiveFullBytes((ex, body) -> {
                //Just echo back the response as 200
                ex.getResponseSender().send(ByteBuffer.wrap(body));
            });
        }
    }

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new RequestDumpingHandler(new EchoHandler(), true));
    }

    @Test
    public void testDateHandler() throws IOException {
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        post.setEntity(new StringEntity("Hello world!"));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(post);
            String responseBody = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hello world!", responseBody);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
