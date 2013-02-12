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

package io.undertow.server.handlers.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
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

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        exchange.addDefaultResponseListener(new DefaultResponseListener() {
            @Override
            public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                if (!exchange.isResponseChannelAvailable()) {
                    return false;
                }
                Set<Integer> codes = responseCodes;
                if (codes == null ? exchange.getResponseCode() >= 400 : codes.contains(Integer.valueOf(exchange.getResponseCode()))) {
                    final String errorPage = "<html><head><title>Error</title></head><body>" + exchange.getResponseCode() + " - " + StatusCodes.getReason(exchange.getResponseCode()) + "</body></html>";
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + errorPage.length());
                    Sender sender = exchange.getResponseSender();
                    sender.send(errorPage, IoCallback.END_EXCHANGE);
                    return true;
                }
                return false;
            }
        });
        HttpHandlers.executeHandler(next, exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    public Set<Integer> getResponseCodes() {
        return Collections.unmodifiableSet(responseCodes);
    }

    public void setResponseCodes(final Set<Integer> responseCodes) {
        this.responseCodes = new HashSet<Integer>(responseCodes);
    }

    public void setResponseCodes(final Integer... responseCodes) {
        this.responseCodes = new HashSet<Integer>(Arrays.asList(responseCodes));
    }
}
