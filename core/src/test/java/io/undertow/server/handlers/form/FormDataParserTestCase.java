/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FormDataParserTestCase {

    @Test
    public void blockingParser() throws Exception {
        final BlockingHandler blocking = new BlockingHandler();

        blocking.setRootHandler(new HttpHandler() {

            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final FormParserFactory parserFactory = FormParserFactory.builder().build();
                final FormDataParser parser = parserFactory.createParser(exchange);
                try {
                    FormData data = parser.parseBlocking();
                    Iterator<String> it = data.iterator();
                    while (it.hasNext()) {
                        String fd = it.next();
                        for (FormData.FormValue val : data.get(fd)) {
                            exchange.responseHeaders().add("res", fd + ":" + val.getValue());
                        }
                    }
                } catch (IOException e) {
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                }
            }
        });
        DefaultServer.setRootHandler(blocking);
        testCase();
    }


    @Test
    public void asyncParser() throws Exception {
        final FormParserFactory parserFactory = FormParserFactory.builder().build();
        HttpHandler fd = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final FormDataParser parser = parserFactory.createParser(exchange);
                parser.parse(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        FormData data = exchange.getAttachment(FormDataParser.FORM_DATA);
                        Iterator<String> it = data.iterator();
                        while (it.hasNext()) {
                            String fd = it.next();
                            for (FormData.FormValue val : data.get(fd)) {
                                exchange.responseHeaders().add("res", fd + ":" + val.getValue());
                            }
                        }
                    }
                });

            }
        };
        DefaultServer.setRootHandler(fd);
        testCase();
    }


    public void testCase() throws Exception {
        runTest(new BasicNameValuePair("name", "A Value"));
        runTest(new BasicNameValuePair("name", "A Value"), new BasicNameValuePair("Single-value", null));
        runTest(new BasicNameValuePair("name", "A Value"), new BasicNameValuePair("A/name/with_special*chars", "A $ value&& with=SomeCharacters"));
        runTest(new BasicNameValuePair("name", "A Value"), new BasicNameValuePair("Single-value", null), new BasicNameValuePair("A/name/with_special*chars", "A $ value&& with=SomeCharacters"));

    }

    private void runTest(final NameValuePair... pairs) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {

            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setHeader(HttpHeaderNames.CONTENT_TYPE, FormEncodedDataDefinition.APPLICATION_X_WWW_FORM_URLENCODED);
            post.setEntity(new UrlEncodedFormEntity(data));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            checkResult(data, result);
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void checkResult(final List<NameValuePair> data, final HttpResponse result) {
        Map<String, String> res = new HashMap<>();
        for (Header d : result.getHeaders("res")) {
            String[] split = d.getValue().split(":");
            res.put(split[0], split.length == 1 ? "" : split[1]);
        }


        for (NameValuePair vp : data) {
            Assert.assertEquals(vp.getValue() == null ? "" : vp.getValue(), res.get(vp.getName()));
        }
    }

}
