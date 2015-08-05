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

import java.util.Locale;
import java.util.Map;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;

/**
 * A {@link HttpHandler} that implements virtual hosts based on the <code>Host:</code> http header
 * header.
 *
 * @author Stuart Douglas
 */
public class NameVirtualHostHandler implements HttpHandler {

    private volatile HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_404;
    private final Map<String, HttpHandler> hosts = new CopyOnWriteMap<>();

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final String hostHeader = exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (hostHeader != null) {
            String host;
            if (hostHeader.contains(":")) { //header can be in host:port format
                host = hostHeader.substring(0, hostHeader.lastIndexOf(":"));
            } else {
                host = hostHeader;
            }
            //most hosts will be lowercase, so we do the host
            HttpHandler handler = hosts.get(host);
            if (handler != null) {
                handler.handleRequest(exchange);
                return;
            }
            //do a cache insensitive match
            handler = hosts.get(host.toLowerCase(Locale.ENGLISH));
            if (handler != null) {
                handler.handleRequest(exchange);
                return;
            }
        }
        defaultHandler.handleRequest(exchange);
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public Map<String, HttpHandler> getHosts() {
        return hosts;
    }

    public NameVirtualHostHandler setDefaultHandler(final HttpHandler defaultHandler) {
        Handlers.handlerNotNull(defaultHandler);
        this.defaultHandler = defaultHandler;
        return this;
    }

    public synchronized NameVirtualHostHandler addHost(final String host, final HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        hosts.put(host.toLowerCase(Locale.ENGLISH), handler);
        return this;
    }

    public synchronized NameVirtualHostHandler removeHost(final String host) {
        hosts.remove(host.toLowerCase(Locale.ENGLISH));
        return this;
    }
}
