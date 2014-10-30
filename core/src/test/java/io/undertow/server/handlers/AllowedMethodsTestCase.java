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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Methods;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
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

    @Test
    public void testAllowedMethods() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final AllowedMethodsHandler handler = new AllowedMethodsHandler(ResponseCodeHandler.HANDLE_200, Methods.POST);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.METHOD_NOT_ALLOWED, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity("foo"));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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
            Assert.assertEquals(StatusCodes.METHOD_NOT_ALLOWED, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity("foo"));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
