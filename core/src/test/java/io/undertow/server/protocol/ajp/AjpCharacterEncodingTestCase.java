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

package io.undertow.server.protocol.ajp;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@Ignore
public class AjpCharacterEncodingTestCase {

    private static final int PORT = DefaultServer.getHostPort() + 10;
    private static Undertow undertow;

    private static OptionMap old;

    @BeforeClass
    public static void setup() throws Exception {
        undertow = Undertow.builder()
                .setServerOption(UndertowOptions.URL_CHARSET, "MS949")
                .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true)
                .addListener(
                        new Undertow.ListenerBuilder()
                                .setType(Undertow.ListenerType.AJP)
                                .setHost(DefaultServer.getHostAddress())
                                .setPort(PORT)
                ).setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("RESULT:" + exchange.getQueryParameters().get("p").getFirst());
                    }
                })
                .build();
        undertow.start();

        DefaultServer.setRootHandler(ProxyHandler.builder().setProxyClient(new LoadBalancingProxyClient().addHost(new URI("ajp://" + DefaultServer.getHostAddress() + ":" + PORT))).build());
        old = DefaultServer.getUndertowOptions();
        DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true, UndertowOptions.URL_CHARSET, "MS949"));
    }

    @AfterClass
    public static void after() {
        DefaultServer.setUndertowOptions(old);
        undertow.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}

    }

    @Test
    public void sendHttpRequest() throws IOException {
        Socket socket = new Socket(DefaultServer.getHostAddress(), DefaultServer.getHostPort());
        socket.getOutputStream().write("GET /path?p=한%20글 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes("MS949"));
        String result = FileUtils.readFile(socket.getInputStream());
        Assert.assertTrue("Failed to find expected result \n" + result, result.contains("한 글"));
    }
}
