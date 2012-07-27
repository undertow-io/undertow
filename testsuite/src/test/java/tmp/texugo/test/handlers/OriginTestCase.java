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

package tmp.texugo.test.handlers;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import tmp.texugo.server.handlers.OriginHandler;
import tmp.texugo.server.handlers.ResponseCodeHandler;
import tmp.texugo.server.handlers.blocking.BlockingHandler;
import tmp.texugo.test.util.DefaultServer;
import tmp.texugo.test.util.HttpClientUtils;
import tmp.texugo.util.Headers;

/**
 * Tests that the Origin header is correctly interpreted
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class OriginTestCase {

    private static final String HEADER = "selected";
    private static final String MESSAGE = "My HTTP Request!";
    private static BlockingHandler blockingHandler;

    /**
     * Tests the Origin header is respected when the strictest options are selected
     *
     */
    @Test
    public void testStrictOrigin() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            final OriginHandler handler = new OriginHandler();
            handler.addAllowedOrigins("http://www.mysite.com:80", "http://mysite.com:80");
            DefaultServer.setRootHandler(handler);
            handler.setNext(ResponseCodeHandler.HANDLE_200);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            HttpResponse result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(403, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ORIGIN, "http://www.mysite.com:80");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ORIGIN, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN, "http://mysite.com:80");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ORIGIN, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN, "bogus");
            result = client.execute(get);
            Assert.assertEquals(403, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            get.setHeader(Headers.ORIGIN, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN, "bogus");
            result = client.execute(get);
            Assert.assertEquals(403, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
