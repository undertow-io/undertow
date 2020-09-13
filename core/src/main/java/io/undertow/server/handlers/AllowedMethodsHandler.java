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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.util.stream.Collectors;

/**
 * Handler that whitelists certain HTTP methods. Only requests with a method in
 * the allowed methods set will be allowed to continue.
 *
 * @author Stuart Douglas
 */
public class AllowedMethodsHandler implements HttpHandler {

    private final Set<HttpString> allowedMethods;
    private final HttpHandler next;

    public AllowedMethodsHandler(final HttpHandler next, final Set<HttpString> allowedMethods) {
        this.allowedMethods = new HashSet<>(allowedMethods);
        this.next = next;
    }

    public AllowedMethodsHandler(final HttpHandler next, final HttpString... allowedMethods) {
        this.allowedMethods = new HashSet<>(Arrays.asList(allowedMethods));
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (allowedMethods.contains(exchange.getRequestMethod())) {
            next.handleRequest(exchange);
        } else {
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

    public Set<HttpString> getAllowedMethods() {
        return Collections.unmodifiableSet(allowedMethods);
    }

    @Override
    public String toString() {

        if (allowedMethods.size() == 1) {
            return "allowed-methods( " + allowedMethods.toArray()[0] + " )";
        } else {
            return "allowed-methods( {" + allowedMethods.stream().map(s -> s.toString()).collect(Collectors.joining(", ")) + "} )";
        }
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "allowed-methods";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("methods", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("methods");
        }

        @Override
        public String defaultParameter() {
            return "methods";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((String[]) config.get("methods"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String[] methods;

        private Wrapper(String[] methods) {
            this.methods = methods;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            HttpString[] strings = new HttpString[methods.length];
            for (int i = 0; i < methods.length; ++i) {
                strings[i] = new HttpString(methods[i]);
            }

            return new AllowedMethodsHandler(handler, strings);
        }
    }
}
