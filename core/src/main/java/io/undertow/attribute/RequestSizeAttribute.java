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
        // initialize requestSize to 2, because of newline the end of headers string
        long requestSize = 2;
        if (exchange.getRequestContentLength() != -1) {
            requestSize += exchange.getRequestContentLength();
        }
        requestSize += calculateRequestLineSize(exchange);
        requestSize += exchange.getRequestHeaders().getHeadersBytes();
        // add 2 bytes for CRLF, and 2 bytes for ": " between header name and value
        requestSize += exchange.getRequestHeaders().size() * 4L;
        return Long.toString(requestSize);
    }

    // Request-Line = Method SP Request-URI SP HTTP-Version CRLF
    private long calculateRequestLineSize(HttpServerExchange exchange) {
        // initialize size to 4, because of CRLF, and 2 spaces in
        long size = 4;
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
            if (token.equals(REQUEST_SIZE) | token.equals(REQUEST_SIZE_SHORT)) {
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
