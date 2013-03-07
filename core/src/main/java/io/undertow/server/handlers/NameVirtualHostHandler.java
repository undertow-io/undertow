/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers;

import java.util.Map;

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
    private final Map<String, HttpHandler> hosts = new CopyOnWriteMap<String, HttpHandler>();


    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final String hostHeader = exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (hostHeader != null) {
            String host;
            if (hostHeader.contains(":")) { //header can be in host:port format
                host = hostHeader.substring(hostHeader.indexOf(":") - 1);
            } else {
                host = hostHeader;
            }
            final HttpHandler handler = hosts.get(host);
            if (handler != null) {
                HttpHandlers.executeHandler(handler, exchange);
                return;
            }
        }
        HttpHandlers.executeHandler(defaultHandler, exchange);
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public Map<String, HttpHandler> getHosts() {
        return hosts;
    }

    public NameVirtualHostHandler setDefaultHandler(final HttpHandler defaultHandler) {
        HttpHandlers.handlerNotNull(defaultHandler);
        this.defaultHandler = defaultHandler;
        return this;
    }

    public synchronized NameVirtualHostHandler addHost(final String host, final HttpHandler handler) {
        HttpHandlers.handlerNotNull(handler);
        hosts.put(host, handler);
        return this;
    }

    public synchronized NameVirtualHostHandler removeHost(final String host) {
        hosts.remove(host);
        return this;
    }
}
