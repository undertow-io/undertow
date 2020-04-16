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

package io.undertow.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 *
 * Handler that appends the JVM route to the session id.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class JvmRouteHandler implements HttpHandler {

    private final HttpHandler next;
    private final String sessionCookieName;
    private final String jvmRoute;
    private final JvmRouteWrapper wrapper = new JvmRouteWrapper();


    public JvmRouteHandler(HttpHandler next, String sessionCookieName, String jvmRoute) {
        this.next = next;
        this.sessionCookieName = sessionCookieName;
        this.jvmRoute = jvmRoute;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        for (Cookie cookie : exchange.requestCookies()) {
            if (sessionCookieName.equals(cookie.getName())) {
                int part = cookie.getValue().indexOf('.');
                if (part != -1) {
                    cookie.setValue(cookie.getValue().substring(0, part));
                }
            }
        }
        exchange.addResponseWrapper(wrapper);
        next.handleRequest(exchange);
    }

    private class JvmRouteWrapper implements ConduitWrapper<StreamSinkConduit> {

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            for (Cookie cookie : exchange.responseCookies()) {
                if (sessionCookieName.equals(cookie.getName())) {
                    StringBuilder sb = new StringBuilder(cookie.getValue());
                    sb.append('.');
                    sb.append(jvmRoute);
                    cookie.setValue(sb.toString());
                }
            }
            return factory.create();
        }
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "jvm-route";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("value", String.class);
            params.put("session-cookie-name", String.class);

            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            String sessionCookieName = (String) config.get("session-cookie-name");

            return new Wrapper((String)config.get("value"), sessionCookieName == null ? "JSESSIONID" : sessionCookieName);
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String value;
        private final String sessionCookieName;

        private Wrapper(String value, String sessionCookieName) {
            this.value = value;
            this.sessionCookieName = sessionCookieName;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new JvmRouteHandler(handler, sessionCookieName, value);
        }
    }
}
