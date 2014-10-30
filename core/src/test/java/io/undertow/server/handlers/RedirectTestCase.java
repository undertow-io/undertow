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

import io.undertow.Handlers;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static io.undertow.Handlers.predicate;
import static io.undertow.Handlers.predicateContext;

/**
 * Tests the redirect handler
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RedirectTestCase {

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new PathHandler()
                .addPrefixPath("/target", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        message = exchange.getRequestURI();
                    }
                })
                .addPrefixPath("/", predicateContext(predicate(Predicates.regex("%{REQUEST_URL}", "/(aa.*?)c", RedirectTestCase.class.getClassLoader(), false),
                        Handlers.redirect("/target/matched/${1}"), Handlers.redirect("/target%U"))))
        );
    }

    @Test
    public void testRedirectHandler() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/a");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Assert.assertEquals("/target/path/a", message );

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/aabc");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Assert.assertEquals("/target/matched/aab", message );

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somePath/aabc");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Assert.assertEquals("/target/matched/aab", message );
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
