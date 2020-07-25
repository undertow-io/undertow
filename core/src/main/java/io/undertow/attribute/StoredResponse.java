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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import io.undertow.UndertowLogger;
import io.undertow.conduits.StoredResponseStreamSinkConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class StoredResponse implements ExchangeAttribute {

    public static final ExchangeAttribute INSTANCE = new StoredResponse();

    private StoredResponse() {

    }

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        byte[] data = exchange.getAttachment(StoredResponseStreamSinkConduit.RESPONSE);
        if(data == null) {
            return null;
        }
        String charset = extractCharset(exchange.getResponseHeaders());
        if(charset == null) {
            return null;
        }
        try {
            return new String(data, charset);
        } catch (UnsupportedEncodingException e) {
            UndertowLogger.ROOT_LOGGER.debugf(e,"Could not decode response body using charset %s", charset);
            return null;
        }
    }
    private String extractCharset(HeaderMap headers) {
        String contentType = headers.getFirst(Headers.CONTENT_TYPE);
        if (contentType != null) {
            String value = Headers.extractQuotedValueFromHeader(contentType, "charset");
            if (value != null) {
                return value;
            }
            //if it is text we default to ISO_8859_1
            if(contentType.startsWith("text/")) {
                return StandardCharsets.ISO_8859_1.displayName();
            }
            return null;
        }
        return null;
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Stored Response", newValue);
    }

    @Override
    public String toString() {
        return "%{STORED_RESPONSE}";
    }

    public static class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Stored Response";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals("%{STORED_RESPONSE}")) {
                return INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
