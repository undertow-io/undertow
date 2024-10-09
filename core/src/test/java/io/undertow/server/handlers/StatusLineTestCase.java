/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests that if the protocol is set to a value, that value is returned on the
 * status line.
 *
 * @author Jeff Okamoto
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class StatusLineTestCase {

    /*
     * For the purposes of the test, the protocol name has to be "HTTP" because the test
     * framework runs through a parser, and it rejects other strings.
     */
    private static final String DEFAULT_PROTOCOL_NAME = "HTTP";
    private static final String DEFAULT_PROTOCOL_MAJOR = "1";
    private static final String DEFAULT_PROTOCOL_MINOR = "1";
    private static final String PROTOCOL_NAME = "HTTP";
    private static final String PROTOCOL_MAJOR = "3";
    private static final String PROTOCOL_MINOR = "4";
    private static final String PROTOCOL_STRING = PROTOCOL_NAME + "/" + PROTOCOL_MAJOR + "." + PROTOCOL_MINOR;
    private static final String REASON_PHRASE = "Reason-Phrase";
    private static final String MESSAGE = "My HTTP Request!";

    private static volatile ServerConnection connection;

    @Test
    public void verifyStatusLine() throws IOException {
        DefaultServer.setRootHandler(new HttpHandler() {

            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                if (connection == null) {
                    connection = exchange.getConnection();
                } else if (!DefaultServer.isAjp()  && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                    Sender sender = exchange.getResponseSender();
                    sender.send("Connection not persistent");
                    return;
                }
                exchange.setProtocol(new HttpString(PROTOCOL_STRING));
                exchange.setReasonPhrase(REASON_PHRASE);
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, MESSAGE.length() + "");
                final Sender sender = exchange.getResponseSender();
                sender.send(MESSAGE);
            }
        });

        connection = null;
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            ProtocolVersion protocolVersion = result.getStatusLine().getProtocolVersion();
            Assert.assertEquals(PROTOCOL_NAME, protocolVersion.getProtocol());
            Assert.assertEquals(Integer.parseInt(PROTOCOL_MAJOR), protocolVersion.getMajor());
            Assert.assertEquals(Integer.parseInt(PROTOCOL_MINOR), protocolVersion.getMinor());

            Assert.assertEquals(REASON_PHRASE, result.getStatusLine().getReasonPhrase());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void verifyDefaultStatusLine() throws IOException {
        DefaultServer.setRootHandler(new HttpHandler() {

            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                if (connection == null) {
                    connection = exchange.getConnection();
                } else if (!DefaultServer.isAjp()  && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                    Sender sender = exchange.getResponseSender();
                    sender.send("Connection not persistent");
                    return;
                }
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, MESSAGE.length() + "");
                final Sender sender = exchange.getResponseSender();
                sender.send(MESSAGE);
            }
        });

        connection = null;
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            ProtocolVersion protocolVersion = result.getStatusLine().getProtocolVersion();
            Assert.assertEquals(DEFAULT_PROTOCOL_NAME, protocolVersion.getProtocol());
            Assert.assertEquals(Integer.parseInt(DEFAULT_PROTOCOL_MAJOR), protocolVersion.getMajor());
            Assert.assertEquals(Integer.parseInt(DEFAULT_PROTOCOL_MINOR), protocolVersion.getMinor());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
