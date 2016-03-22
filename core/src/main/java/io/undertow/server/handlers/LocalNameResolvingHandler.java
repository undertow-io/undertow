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

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A handler that performs DNS lookup to resolve a local address. Unresolved local address may be created when a front
 * end server has sent a X-forwarded-host header or AJP is in use
 *
 * @author Stuart Douglas
 */
public class LocalNameResolvingHandler implements HttpHandler {

    private final HttpHandler next;
    private final ResolveType resolveType;

    public LocalNameResolvingHandler(HttpHandler next) {
        this.next = next;
        this.resolveType = ResolveType.FORWARD_AND_REVERSE;
    }

    public LocalNameResolvingHandler(HttpHandler next, ResolveType resolveType) {
        this.next = next;
        this.resolveType = resolveType;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final InetSocketAddress address = exchange.getDestinationAddress();
        if (address != null) {
            if ((resolveType == ResolveType.FORWARD || resolveType == ResolveType.FORWARD_AND_REVERSE)
                    && address.isUnresolved()) {
                try {
                    if (System.getSecurityManager() == null) {
                        final InetSocketAddress resolvedAddress = new InetSocketAddress(InetAddress.getByName(address.getHostName()), address.getPort());
                        exchange.setDestinationAddress(resolvedAddress);
                    } else {
                        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                            @Override
                            public Object run() throws UnknownHostException {
                                final InetSocketAddress resolvedAddress = new InetSocketAddress(InetAddress.getByName(address.getHostName()), address.getPort());
                                exchange.setDestinationAddress(resolvedAddress);
                                return null;
                            }
                        });
                    }
                } catch (UnknownHostException e) {
                    UndertowLogger.REQUEST_LOGGER.debugf(e, "Could not resolve hostname %s", address.getHostString());
                }

            } else if (resolveType == ResolveType.REVERSE || resolveType == ResolveType.FORWARD_AND_REVERSE) {
                if (System.getSecurityManager() == null) {
                    address.getHostName();
                } else {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            address.getHostName();
                            return null;
                        }
                    });
                }
                //we call set source address because otherwise the underlying channel could just return a new address
                exchange.setDestinationAddress(address);
            }
        }

        next.handleRequest(exchange);
    }

    public enum ResolveType {
        FORWARD,
        REVERSE,
        FORWARD_AND_REVERSE

    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "resolve-local-name";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new LocalNameResolvingHandler(handler);
        }
    }


}
