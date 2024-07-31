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
import io.undertow.util.StatusCodes;

/**
 * Size of response in bytes, including headers
 *
 * @author Marek Jusko
 */

public class ResponseSizeAttribute implements ExchangeAttribute{

    public static final String RESPONSE_SIZE_SHORT = "%O";
    public static final String RESPONSE_SIZE = "%{RESPONSE_SIZE}";
    public static final ExchangeAttribute INSTANCE = new ResponseSizeAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        if (exchange.getResponseHeaders().size() == 0) {
            return "0";
        }
        // Initialize requestSize to 2 bytes for the CRLF at the end of headers string
        long responseSize = 2;

        // Add the number of bytes sent in the response body
        responseSize += exchange.getResponseBytesSent();

        // Add the size of the status line
        responseSize += calculateStatusLineSize(exchange);

        // Add the size of the headers
        responseSize += exchange.getResponseHeaders().getHeadersBytes();

        // Add 4 bytes per header for ": " and CRLF
        responseSize += exchange.getResponseHeaders().size() * 4L;

        return Long.toString(responseSize);
    }

    // Status Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
    private long calculateStatusLineSize(HttpServerExchange exchange) {
        // Initialize size to 7 bytes for the spaces, CRLF, and 3-digit status code
        long size = 7; // 3 for status code + 2 for spaces + 2 for CRLF

        // Add the length of the HTTP version
        size += exchange.getProtocol().length();

        // Add the length of the status message
        size += StatusCodes.getReason(exchange.getStatusCode()).length();

        return size;
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Size of response, including headers", newValue);
    }

    @Override
    public String toString() {
        return RESPONSE_SIZE;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response size";
        }

        @Override
        public ExchangeAttribute build(String token) {
            if (token.equals(RESPONSE_SIZE) || token.equals(RESPONSE_SIZE_SHORT)) {
                return ResponseSizeAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
