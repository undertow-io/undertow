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

package io.undertow.server.handlers.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.undertow.Handlers;
import io.undertow.io.Sender;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * Handler that generates an extremely simple no frills error page
 *
 * @author Stuart Douglas
 */
public class SimpleErrorPageHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    /**
     * The response codes that this handler will handle. If this is null then it will handle all 4xx and 5xx codes.
     */
    private volatile Set<Integer> responseCodes = null;

    public SimpleErrorPageHandler(final HttpHandler next) {
        this.next = next;
    }

    public SimpleErrorPageHandler() {
    }

    private final DefaultResponseListener responseListener = new DefaultResponseListener() {
        @Override
        public boolean handleDefaultResponse(final HttpServerExchange exchange) {
            if (!exchange.isResponseChannelAvailable()) {
                return false;
            }
            Set<Integer> codes = responseCodes;
            if (codes == null ? exchange.getStatusCode() >= StatusCodes.BAD_REQUEST : codes.contains(Integer.valueOf(exchange.getStatusCode()))) {
                final String errorPage = "<html><head><title>Error</title></head><body>" + exchange.getStatusCode() + " - " + StatusCodes.getReason(exchange.getStatusCode()) + "</body></html>";
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + errorPage.length());
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                Sender sender = exchange.getResponseSender();
                sender.send(errorPage);
                return true;
            }
            return false;
        }
    };

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addDefaultResponseListener(responseListener);
        next.handleRequest(exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public SimpleErrorPageHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public Set<Integer> getResponseCodes() {
        return Collections.unmodifiableSet(responseCodes);
    }

    public SimpleErrorPageHandler setResponseCodes(final Set<Integer> responseCodes) {
        this.responseCodes = new HashSet<>(responseCodes);
        return this;
    }

    public SimpleErrorPageHandler setResponseCodes(final Integer... responseCodes) {
        this.responseCodes = new HashSet<>(Arrays.asList(responseCodes));
        return this;
    }
}
