/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.test.handlers;

import java.io.IOException;

import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.DisallowedMethodsHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Methods;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that the allowed and disallowed method handlers work as expected
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class AllowedMethodsTestCase {

    private static final String HEADER = "selected";
    private static final String MESSAGE = "My HTTP Request!";
    private static BlockingHandler blockingHandler;

    @Test
    public void testAllowedMethods() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final AllowedMethodsHandler handler = new AllowedMethodsHandler(ResponseCodeHandler.HANDLE_200, Methods.POST);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(405, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity("foo"));
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDisallowedMethods() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final DisallowedMethodsHandler handler = new DisallowedMethodsHandler(ResponseCodeHandler.HANDLE_200, Methods.GET);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(405, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity("foo"));
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
