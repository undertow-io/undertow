/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * Class which handles set operations for response: code, reason phrase and potentially body and type. Status code is required
 * parameter.<br>
 * The response handler allows to set response body as well. <br>
 * response(code=404, reason='dont like it') is roughly equivalent to reason-phrase('dont like it');response-code(404)"<br>
 *
 * @author <a href="mailto:bbaranow@redhat.com">Bartosz Baranowski</a>
 */
public class ResponseHandler implements HttpHandler {

    private static final String DEFAULT_BODY_TYPE = "text/html";
    private static final boolean debugEnabled;

    static {
        debugEnabled = UndertowLogger.PREDICATE_LOGGER.isDebugEnabled();
    }

    private final String body;
    private final String type;
    private final int code;
    private final String reason;
    private HttpHandler chained;

    // TODO: review parsing/execution rules. For some reason without next, this particular handler does not ignore
    // trailing handlers.
    public ResponseHandler(final int code, final String reason) {
        this(code, reason, null, null);
    }

    public ResponseHandler(final int code, final String reason, final String body) {
        this(code, reason, body, DEFAULT_BODY_TYPE);
    }

    public ResponseHandler(final int code, final String reason, final String body, final String type) {
        this.body = body;
        this.type = type;
        // toString only
        this.code = code;
        this.reason = reason;
        if (reason != null) {
            this.chained = new ReasonPhraseHandler(null, reason);
        }
        this.chained = new ResponseCodeHandler(this.chained, code);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        this.chained.handleRequest(exchange);
        if (this.body != null) {
            final byte[] bodyBytes = this.body.getBytes("UTF-8");
            final HeaderMap responseHeaders = exchange.getResponseHeaders();
            if (responseHeaders.contains(Headers.CONTENT_LENGTH) || responseHeaders.contains(Headers.CONTENT_TYPE) || exchange.isBlocking()) {
                //TODO: need user feedback
                throw UndertowMessages.MESSAGES.exchangeBlockingOrBlocking(exchange);
            }
            responseHeaders.add(Headers.CONTENT_TYPE, this.type);
            responseHeaders.add(Headers.CONTENT_LENGTH, bodyBytes.length);
            exchange.startBlocking();
            if (exchange.isInIoThread()) {
                exchange.dispatch(new HttpHandler() {

                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getOutputStream().write(bodyBytes);
                    }
                });
            } else {
                exchange.getOutputStream().write(bodyBytes);
            }

            if (debugEnabled) {
                UndertowLogger.PREDICATE_LOGGER.debugf("Respons body set to \n[%s]\nfor %s.", this.body, exchange);
            }
        }
    }

    @Override
    public String toString() {
        return "response( code='" + code + "'" + ((this.reason != null) ? ", reason='" + this.reason + "'" : "") + ""
                + ((this.body != null) ? ", type='" + this.type + "', body='" + this.body + "'" : "") + " )";
    }

}
