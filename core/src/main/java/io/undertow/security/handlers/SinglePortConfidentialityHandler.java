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
package io.undertow.security.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.NetworkUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * An extension to {@link AbstractConfidentialityHandler} that uses the Host header from the incoming message and specifies the
 * confidential address by just switching the port.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SinglePortConfidentialityHandler extends AbstractConfidentialityHandler {

    private final int redirectPort;

    public SinglePortConfidentialityHandler(final HttpHandler next, final int redirectPort) {
        super(next);
        this.redirectPort = redirectPort == 443 ? -1 : redirectPort;
    }

    @Override
    protected URI getRedirectURI(HttpServerExchange exchange) throws URISyntaxException {
        return getRedirectURI(exchange, redirectPort);
    }

    protected URI getRedirectURI(final HttpServerExchange exchange, final int port) throws URISyntaxException {
        final StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append("https://");
        uriBuilder.append(NetworkUtils.formatPossibleIpv6Address(exchange.getHostName()));
        if (port > 0) {
            uriBuilder.append(":").append(port);
        }
        String uri = exchange.getRequestURI();
        if(exchange.isHostIncludedInRequestURI()) {
            int slashCount = 0;
            for(int i = 0; i < uri.length(); ++i) {
                if(uri.charAt(i) == '/') {
                    slashCount++;
                    if(slashCount == 3) {
                        uri = uri.substring(i);
                        break;
                    }
                }
            }
        }
        uriBuilder.append(uri);
        final String queryString = exchange.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            uriBuilder.append("?").append(queryString);
        }
        return new URI(uriBuilder.toString());
    }

}
