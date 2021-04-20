/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.server.protocol.http2;

import java.net.URISyntaxException;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.Options;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class HTTP2ViaUpgradeWithUnEncodedURLCharactersTestCase extends HTTP2ViaUpgradeTestCase{

    @BeforeClass
    public static void setup() throws URISyntaxException {
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        int port = DefaultServer.getHostPort("default");
        server = Undertow.builder()
                .addHttpListener(port + 1, DefaultServer.getHostAddress("default"))
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(Handlers.header(new Http2UpgradeHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        exchange.getResponseSender().send(message);
                    }
                }, "h2c", "h2c-17"), Headers.SEC_WEB_SOCKET_ACCEPT_STRING, "fake")) //work around Netty bug, it assumes that every upgrade request that does not have this header is an old style websocket upgrade
                .build();

        server.start();
    }

    protected String fetchUpgradeHandlerURL() {
        return "/^?query=^";
    }
}
