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

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.URLUtils;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * Tests that query parameters are handled correctly.
 *
 * @author Stuart Douglas
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public abstract class AbstractQueryParametersTest {

    // in the format: queryString, expected result
    protected static String[][] queryStrings = null;

    @BeforeClass
    public static void setup (){
        DefaultServer.setRootHandler(exchange -> {
            StringBuilder sb = new StringBuilder();
            sb.append(exchange.getQueryString());
            sb.append("{");
            Iterator<Map.Entry<String,Deque<String>>> iterator = exchange.getQueryParameters().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Deque<String>> qp = iterator.next();
                sb.append(qp.getKey());
                sb.append("=>");
                if(qp.getValue().size() == 1) {
                    sb.append(qp.getValue().getFirst());
                } else {
                    sb.append("[");
                    for(Iterator<String> i = qp.getValue().iterator(); i.hasNext(); ) {
                        String val = i.next();
                        sb.append(val);
                        if(i.hasNext()) {
                            sb.append(",");
                        }
                    }
                    sb.append("]");
                }
                if(iterator.hasNext()) {
                    sb.append(",");
                }

            }
            sb.append("}");
            exchange.getResponseSender().send(sb.toString());
        });
    }


    @Test
    public void testQueryParameters() throws IOException {
        try (TestHttpClient client = new TestHttpClient()) {
            for (String[] queryStringPair: queryStrings) {
                runTest(client, queryStringPair[1], queryStringPair[0]);
            }
        }
    }

    @Test
    public void testQueryParametersShiftJIS() throws IOException {
        OptionMap old = DefaultServer.getUndertowOptions();
        try {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.URL_CHARSET, "Shift_JIS"));
            try (TestHttpClient client = new TestHttpClient()) {
                runTest(client, "unicode=%83e%83X%83g{unicode=>テスト}", "/path?unicode=%83e%83X%83g");

            }
        } finally {
            DefaultServer.setUndertowOptions(old);
        }
    }

    @Test
    public void testQueryParameterParsingIncorrectlyEncodedURI() throws ParameterLimitException {
        String s = "p=" + (char) 0xc7 + (char) 0xd1 + (char) 0x25 + (char) 0x32 + (char) 0x30 + (char) 0xb1 + (char) 0xdb;
        HttpServerExchange exchange = new HttpServerExchange(null);
        URLUtils.parseQueryString(s, exchange, "MS949", true, 1000);
        Assert.assertEquals("한 글", exchange.getQueryParameters().get("p").getFirst());

    }

    private void runTest(final TestHttpClient client, final String expected, final String queryString) throws IOException {
        Assert.assertEquals(expected, HttpClientUtils.readResponse(client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + queryString))));
    }
}
