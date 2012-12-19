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

package io.undertow.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.AjpIgnore;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.HttpString;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@AjpIgnore
@RunWith(DefaultServer.class)
public class SimpleSSLTestCase {


    @Test
    public void simpleSSLTestCase() throws IOException, GeneralSecurityException {

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                completionHandler.handleComplete();
            }
        });

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders("scheme");
            Assert.assertEquals("https", header[0].getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
