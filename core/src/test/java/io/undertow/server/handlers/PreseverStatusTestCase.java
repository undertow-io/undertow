/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(DefaultServer.class)
@HttpOneOnly // Http2 does NOT have way to transfer status: https://httpwg.org/specs/rfc7540.html#rfc.section.8.1.2.4
             // https://www.rfc-editor.org/rfc/rfc9113.html#name-response-pseudo-header-fiel
public class PreseverStatusTestCase {
    private static final String CUSTOM_REASON = "Its just a flesh wound";

    @Before
    public void setup() {

        DefaultServer.setRootHandler(new BlockingHandler(exchange -> {
            exchange.setStatusCode(StatusCodes.TOO_MANY_REQUESTS);
            exchange.setReasonPhrase(CUSTOM_REASON);
        }));
    }

    @Test
    public void testWindowSliding() {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.TOO_MANY_REQUESTS, result.getCode());
                Assert.assertEquals(CUSTOM_REASON, result.getReasonPhrase());
                return null;
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
