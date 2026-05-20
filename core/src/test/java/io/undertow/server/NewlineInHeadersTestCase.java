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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class NewlineInHeadersTestCase {

    private static final String RESPONSE = "response";
    private static final String ECHO = "echo";

    @Test
    public void testNewlineInHeaders() throws IOException {
        DefaultServer.setRootHandler(exchange -> exchange.getRequestReceiver()
                .receiveFullString((exchange1, message) -> {
                    exchange1.getResponseHeaders().put(HttpString.tryFromString(ECHO), message);
                    exchange1.getResponseSender().send(RESPONSE);
                }));
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL());
            post.setEntity(new StringEntity("test"));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("test", result.getFirstHeader(ECHO).getValue());
                Assert.assertEquals(RESPONSE, HttpClientUtils.readResponse(result));
                return null;
            });

            post = new HttpPost(DefaultServer.getDefaultServerURL());
            post.setEntity(new StringEntity("test\nnewline"));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("test newline", result.getFirstHeader(ECHO).getValue());
                Assert.assertEquals(RESPONSE, HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
