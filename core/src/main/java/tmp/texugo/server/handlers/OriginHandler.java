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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import tmp.texugo.TexugoLogger;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.Headers;

/**
 * A handler for the HTTP Origin header.
 *
 * @author Stuart Douglas
 */
public class OriginHandler implements HttpHandler {

    private volatile Set<String> allowedOrigins = new HashSet<String>();
    private volatile boolean requireAllOrigins = true;
    private volatile boolean requireOriginHeader = true;
    private volatile HttpHandler next;


    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final Deque<String> origin = exchange.getRequestHeaders().get(Headers.ORIGIN);
        if (origin == null) {
            if (requireOriginHeader) {
                //TODO: Is 403 (Forbidden) the best reponse code
                if (TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    TexugoLogger.REQUEST_LOGGER.debugf("Refusing request for %s due to lack of Origin: header", exchange.getRequestPath());
                }
                exchange.setResponseCode(403);
                return;
            }
        } else {
            boolean found = false;
            final boolean requireAllOrigins = this.requireAllOrigins;
            for (final String header : origin) {
                if (allowedOrigins.contains(header)) {
                    found = true;
                    if (!requireAllOrigins) {
                        break;
                    }
                } else if (requireAllOrigins) {
                    if (TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        TexugoLogger.REQUEST_LOGGER.debugf("Refusing request for %s due to Origin %s not being in the allowed origins list", exchange.getRequestPath(), header);
                    }
                    exchange.setResponseCode(403);
                    return;
                }
            }
            if (!found) {
                if (TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    TexugoLogger.REQUEST_LOGGER.debugf("Refusing request for %s as none of the specified origins %s were in the allowed origins list", exchange.getRequestPath(), origin);
                }
                exchange.setResponseCode(403);
                return;
            }
        }
        HttpHandler next = this.next;
        if(next != null) {
            next.handleRequest(exchange);
        }
    }

    public synchronized void addAllowedOrigin(final String origin) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.add(origin);
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized void addAllowedOrigins(final Collection<String> origins) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.addAll(origins);
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized void addAllowedOrigins(final String... origins) {
        final Set<String> allowedOrigins = new HashSet<String>(this.allowedOrigins);
        allowedOrigins.addAll(Arrays.asList(origins));
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
    }

    public synchronized Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public synchronized void clearAllowedOrigins() {
        this.allowedOrigins = Collections.emptySet();
    }

    public boolean isRequireAllOrigins() {
        return requireAllOrigins;
    }

    public void setRequireAllOrigins(final boolean requireAllOrigins) {
        this.requireAllOrigins = requireAllOrigins;
    }

    public boolean isRequireOriginHeader() {
        return requireOriginHeader;
    }

    public void setRequireOriginHeader(final boolean requireOriginHeader) {
        this.requireOriginHeader = requireOriginHeader;
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        this.next = next;
    }
}
