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
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

/**
 * Tests that query parameters are handled correctly.
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class QueryParametersTestCase {


    @BeforeClass
    public static void setup (){
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                StringBuilder sb = new StringBuilder();
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
            }
        });
    }


    @Test
    public void testQueryParameters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(client, "{unicode=>Iñtërnâtiônàližætiøn}", "/path?unicode=Iñtërnâtiônàližætiøn");
            runTest(client, "{a=>b,value=>bb bb}", "/path?a=b&value=bb%20bb");
            runTest(client, "{a=>b,value=>[bb,cc]}", "/path?a=b&value=bb&value=cc");
            runTest(client, "{a=>b,s =>,t =>,value=>[bb,cc]}", "/path?a=b&value=bb&value=cc&s%20&t%20");
            runTest(client, "{a=>b,s =>,t =>,value=>[bb,cc]}", "/path?a=b&value=bb&value=cc&s%20&t%20&");
            runTest(client, "{a=>b,s =>,t =>,u=>,value=>[bb,cc]}", "/path?a=b&value=bb&value=cc&s%20&t%20&u");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }



    @Test
    @ProxyIgnore
    public void testQueryParametersShiftJIS() throws IOException {
        OptionMap old = DefaultServer.getUndertowOptions();
        try {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.URL_CHARSET, "Shift_JIS"));
            TestHttpClient client = new TestHttpClient();
            try {
                runTest(client, "{unicode=>テスト}", "/path?unicode=%83e%83X%83g");

            } finally {
                client.getConnectionManager().shutdown();
            }
        } finally {
            DefaultServer.setUndertowOptions(old);
        }
    }

    private void runTest(final TestHttpClient client, final String expected, final String queryString) throws IOException {
        Assert.assertEquals(expected, HttpClientUtils.readResponse(client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + queryString))));
    }


}
