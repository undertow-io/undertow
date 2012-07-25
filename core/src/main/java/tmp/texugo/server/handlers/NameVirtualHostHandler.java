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

package tmp.texugo.server.handlers;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import tmp.texugo.TexugoMessages;
import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.Headers;

/**
 * A {@link HttpHandler} that implements virtual hosts based on the <code>Host:</code> http header
 * header.
 *
 * @author Stuart Douglas
 */
public class NameVirtualHostHandler implements HttpHandler {

    private volatile HttpHandler defaultHandler;
    private volatile Map<String, HttpHandler> hosts;

    public NameVirtualHostHandler(final HttpHandler defaultHandler, final Map<String, HttpHandler> hosts) {
        if(defaultHandler == null) {
            throw TexugoMessages.MESSAGES.noDefaultHandlerSpecified();
        }
        this.defaultHandler = defaultHandler;
        this.hosts = Collections.unmodifiableMap(new HashMap<String, HttpHandler>(hosts));
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final Deque<String> host = exchange.getRequestHeaders().get(Headers.HOST);
        if(host != null) {
            final HttpHandler handler = hosts.get(host.getFirst());
            if(handler != null) {
                handler.handleRequest(exchange, completionHandler);
            }
        }
        defaultHandler.handleRequest(exchange, completionHandler);
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public Map<String, HttpHandler> getHosts() {
        return hosts;
    }

    public void setDefaultHandler(final HttpHandler defaultHandler) {
        if(defaultHandler == null) {
            throw TexugoMessages.MESSAGES.noDefaultHandlerSpecified();
        }
        this.defaultHandler = defaultHandler;
    }

    public synchronized void addHost(final String host, final HttpHandler handler) {
        final Map<String, HttpHandler> hosts = new HashMap<String, HttpHandler>(this.hosts);
        hosts.put(host, handler);
        this.hosts = Collections.unmodifiableMap(hosts);
    }

    public synchronized void removeHost(final String host) {
        final Map<String, HttpHandler> hosts = new HashMap<String, HttpHandler>(this.hosts);
        hosts.remove(host);
        this.hosts = Collections.unmodifiableMap(hosts);
    }
}
