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

package io.undertow.server.handlers.error;

import java.io.IOException;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleErrorPageHandlerTestCase {


    @Test
    public void testSimpleErrorPageIsGenerated() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final SimpleErrorPageHandler handler = new SimpleErrorPageHandler();
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);

            Assert.assertTrue(response, response.contains(StatusCodes.NOT_FOUND_STRING));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
