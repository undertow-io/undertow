/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package tmp.texugo.server.handlers;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import tmp.texugo.TexugoMessages;
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

    private final HttpHandler defaultHandler;
    private final Map<String, HttpHandler> hosts;

    public NameVirtualHostHandler(final HttpHandler defaultHandler, final Map<String, HttpHandler> hosts) {
        if(defaultHandler == null) {
            throw TexugoMessages.MESSAGES.noDefaultHandlerSpecified();
        }
        this.defaultHandler = defaultHandler;
        this.hosts = Collections.unmodifiableMap(new HashMap<String, HttpHandler>(hosts));
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final Deque<String> host = exchange.getRequestHeaders().get(Headers.HOST);
        if(host != null) {
            final HttpHandler handler = hosts.get(host.getFirst());
            if(handler != null) {
                handler.handleRequest(exchange);
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
}
