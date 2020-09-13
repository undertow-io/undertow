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
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

/**
 * A request header
 *
 * @author Stuart Douglas
 */
public class RequestHeaderAttribute implements ExchangeAttribute {


    private final HttpString requestHeader;

    public RequestHeaderAttribute(final HttpString requestHeader) {
        this.requestHeader = requestHeader;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        HeaderValues header = exchange.getRequestHeaders().get(requestHeader);
        if (header == null) {
            return null;
        } else if(header.size() == 1) {
            return header.getFirst();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < header.size(); ++i) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(header.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.getRequestHeaders().put(requestHeader, newValue);
    }

    @Override
    public String toString() {
        return "%{i," + requestHeader + "}";
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request header";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{i,") && token.endsWith("}")) {
                final HttpString headerName = HttpString.tryFromString(token.substring(4, token.length() - 1));
                return new RequestHeaderAttribute(headerName);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
