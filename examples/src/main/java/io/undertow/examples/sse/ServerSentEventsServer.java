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

package io.undertow.examples.sse;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.StringReadChannelListener;

import java.io.IOException;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.serverSentEvents;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Server Sent Events")
public class ServerSentEventsServer {


    public static void main(final String[] args) {
        final ServerSentEventHandler sseHandler = serverSentEvents();
        HttpHandler chatHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                new StringReadChannelListener(exchange.getConnection().getByteBufferPool()) {

                    @Override
                    protected void stringDone(String string) {
                        for(ServerSentEventConnection h : sseHandler.getConnections()) {
                            h.send(string);
                        }
                    }

                    @Override
                    protected void error(IOException e) {

                    }
                }.setup(exchange.getRequestChannel());
            }
        };
        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(path()
                        .addPrefixPath("/sse", sseHandler)
                        .addPrefixPath("/send", chatHandler)
                        .addPrefixPath("/", resource(new ClassPathResourceManager(ServerSentEventsServer.class.getClassLoader(), ServerSentEventsServer.class.getPackage())).addWelcomeFiles("index.html")))
                .build();
        server.start();
    }

}
