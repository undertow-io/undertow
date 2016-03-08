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
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PredicatedHandlersTestCase {

    @Test
    public void testRewrite() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(

                        PredicatedHandlersParser.parse(
                                        "path(/skipallrules) and true -> done\n" +
                                        "method(GET) -> set(attribute='%{o,type}', value=get) \r\n" +
                                        "regex('(.*).css') -> {rewrite['${1}.xcss'];set(attribute='%{o,chained}', value=true)} \n" +
                                        "regex('(.*).redirect$') -> redirect['${1}.redirected']\n\n\n\n\n" +
                                        "set[attribute='%{o,someHeader}', value=always]\n" +
                                        "path-template('/foo/{bar}/{f}') -> set[attribute='%{o,template}', value='${bar}']\r\n" +
                                        "path-template('/bar->foo') -> redirect(/);" +
                                        "regex('(.*).css') -> set[attribute='%{o,css}', value='true'] else set[attribute='%{o,css}', value='false']; " +
                                        "path(/restart) -> {rewrite(/foo/a/b); restart; }\r\n", getClass().getClassLoader()), new HttpHandler() {
                            @Override
                            public void handleRequest(HttpServerExchange exchange) throws Exception {
                                exchange.getResponseSender().send(exchange.getRelativePath());
                            }
                        }));

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());
            Assert.assertEquals("false", result.getHeaders("css")[0].getValue());
            Assert.assertEquals("/foo/a/b", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b.css");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("true", result.getHeaders("chained")[0].getValue());
            Assert.assertEquals("/foo/a/b.xcss", response);
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("true", result.getHeaders("css")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b.redirect");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());
            Assert.assertEquals("/foo/a/b.redirected", response);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/skipallrules");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(0, result.getHeaders("someHeader").length);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/restart");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("get", result.getHeaders("type")[0].getValue());
            Assert.assertEquals("always", result.getHeaders("someHeader")[0].getValue());
            Assert.assertEquals("a", result.getHeaders("template")[0].getValue());
            Assert.assertEquals("/foo/a/b", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
