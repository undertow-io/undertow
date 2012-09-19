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

package io.undertow.test;

import java.io.IOException;

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MaxRequestSizeTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(ResponseCodeHandler.HANDLE_200);
    }

    @Test
    public void testMaxRequestSize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try {
            final DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            get.addHeader(Headers.CONNECTION, "close");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 10);
            DefaultServer.setUndertowOptions(maxSize);

            try {
                get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
                get.addHeader(Headers.CONNECTION, "close");
                result = client.execute(get);
                Assert.fail("request should have been too big");
            } catch (IOException e) {
                //expected
            }

            maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 1000);
            DefaultServer.setUndertowOptions(maxSize);
            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            get.addHeader(Headers.CONNECTION, "close");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }

}
