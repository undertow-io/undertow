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

package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * Size of request in bytes, including headers, cannot be zero
 *
 * @author Marek Jusko
 */

public class RequestSizeAttribute implements ExchangeAttribute{

    public static final String REQUEST_SIZE_SHORT = "%E";
    public static final String REQUEST_SIZE = "%{REQUEST_SIZE}";
    public static final ExchangeAttribute INSTANCE = new RequestSizeAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        // Initialize requestSize to 2 bytes for the CRLF at the end of headers string
        long requestSize = 2;

        // Add the request content length if it is specified
        if (exchange.getRequestContentLength() != -1) {
            requestSize += exchange.getRequestContentLength();
        }
        // Add the size of the request line
        requestSize += calculateRequestLineSize(exchange);

        // Add the size of all headers
        requestSize += exchange.getRequestHeaders().getHeadersBytes();

        // Add 4 bytes per header for ": " and CRLF
        requestSize += exchange.getRequestHeaders().size() * 4L;

        return Long.toString(requestSize);
    }

    // Request-Line = Method SP Request-URI SP HTTP-Version CRLF
    private long calculateRequestLineSize(HttpServerExchange exchange) {
        // Initialize size to 4 bytes for the 2 spaces and CRLF in the request line
        long size = 4; // 2 spaces + CRLF

        // Add the length of the protocol, request method, and request path
        size += exchange.getProtocol().length();
        size += exchange.getRequestMethod().length();
        size += exchange.getRequestPath().length();

        return size;
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Size of request, including headers", newValue);
    }

    @Override
    public String toString() {
        return REQUEST_SIZE;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request size";
        }

        @Override
        public ExchangeAttribute build(String token) {
            if (token.equals(REQUEST_SIZE) || token.equals(REQUEST_SIZE_SHORT)) {
                return RequestSizeAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
