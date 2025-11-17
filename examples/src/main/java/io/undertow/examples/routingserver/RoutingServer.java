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
package io.undertow.examples.routingserver;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;

import java.nio.charset.StandardCharsets;

/**
 * An example of how to use the RoutingHandler to dispatch
 * to different handlers based on the request method (GET, POST, etc.).
 *
 * Demonstrates:
 * - GET /greet
 * - POST /greet
 * - Fallback 404 handler
 *
 * Author: anamitraupadhyay
 */
@UndertowExample("Routing Handler")
public class RoutingServer {

    public static void main(final String[] args) {
        RoutingHandler handler = Handlers.routing()
                .get("/greet", RoutingServer::handleGetRequest)
                .post("/greet", RoutingServer::handlePostRequest)
                .get("/*", exchange -> {
                    exchange.setStatusCode(404);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send("Page Not Found");
                });

        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(handler)
                .build();
        server.start();
    }

    private static void handleGetRequest(HttpServerExchange exchange) {
        String name = "World";
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("Hello " + name);
    }

    private static void handlePostRequest(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullString(
                (HttpServerExchange exc, String body) -> {
                    exc.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exc.getResponseSender().send("Hello " + body);
                },
                StandardCharsets.UTF_8
        );
    }
}
