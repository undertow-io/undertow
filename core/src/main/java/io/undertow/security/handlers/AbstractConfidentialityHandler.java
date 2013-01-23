/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.security.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.Headers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handler responsible for checking of confidentiality is required for the requested resource and if so rejecting the request
 * and redirecting to a secure address.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class AbstractConfidentialityHandler implements HttpHandler {

    private final HttpHandler next;

    protected AbstractConfidentialityHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        if (isConfidential(exchange) || (confidentialityRequired(exchange) == false)) {
            HttpHandlers.executeHandler(next, exchange, completionHandler);
        } else {
            try {
                URI redirectUri = getRedirectURI(exchange);

                exchange.setResponseCode(302);
                exchange.getResponseHeaders().put(Headers.LOCATION, redirectUri.toString());
            } catch (URISyntaxException e) {
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
                exchange.setResponseCode(500);
            }
            completionHandler.handleComplete();
        }
    }

    /**
     * Use the HttpServerExchange supplied to check if this request is already 'sufficiently' confidential.
     *
     * Here we say 'sufficiently' as sub-classes can override this and maybe even go so far as querying the actual SSLSession.
     *
     * @param exchange - The {@see HttpServerExchange} for the request being processed.
     * @return true if the request is 'sufficiently' confidential, false otherwise.
     */
    protected boolean isConfidential(final HttpServerExchange exchange) {
        return exchange.getRequestScheme().equals("https");
    }

    /**
     * Use the HttpServerExchange to identify if confidentiality is required.
     *
     * This method currently returns true for all requests, sub-classes can override this to provide a custom check.
     *
     * @param exchange - The {@see HttpServerExchange} for the request being processed.
     * @return true if the request requires confidentiality, false otherwise.
     */
    protected boolean confidentialityRequired(final HttpServerExchange exchange) {
        return true;
    }

    /**
     * All sub-classes are required to provide an implementation of this method, using the HttpServerExchange for the current
     * request return the address to use for a redirect should confidentiality be required and the request not be confidential.
     *
     * @param exchange - The {@see HttpServerExchange} for the request being processed.
     * @return The {@see URI} to redirect to.
     */
    protected abstract URI getRedirectURI(final HttpServerExchange exchange) throws URISyntaxException;

}
