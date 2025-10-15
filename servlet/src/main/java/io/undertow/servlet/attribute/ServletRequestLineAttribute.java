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

package io.undertow.servlet.attribute;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeBuilder;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.attribute.RequestLineAttribute;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

import jakarta.servlet.RequestDispatcher;

/**
 * The request line
 *
 * @author Stuart Douglas
 */
public class ServletRequestLineAttribute implements ExchangeAttribute {

    public static final String REQUEST_LINE_SHORT = "%r";
    public static final String REQUEST_LINE = "%{REQUEST_LINE}";

    public static final ExchangeAttribute INSTANCE = new ServletRequestLineAttribute();

    private ServletRequestLineAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (src == null) {
            return RequestLineAttribute.INSTANCE.readAttribute(exchange);
        }
        StringBuilder sb = new StringBuilder()
                .append(exchange.getRequestMethod().toString())
                .append(' ')
                .append(ServletRequestURLAttribute.INSTANCE.readAttribute(exchange));
        String query = (String) src.getServletRequest().getAttribute(RequestDispatcher.FORWARD_QUERY_STRING);
        if (query != null && !query.isEmpty()) {
            sb.append('?');
            sb.append(query);
        } else if (!exchange.getQueryString().isEmpty()) {
            sb.append('?');
            sb.append(exchange.getQueryString());
        }
        sb.append(' ')
                .append(exchange.getProtocol().toString()).toString();
        return sb.toString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Request line", newValue);
    }

    @Override
    public String toString() {
        return REQUEST_LINE;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request line";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_LINE) || token.equals(REQUEST_LINE_SHORT)) {
                return ServletRequestLineAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 1;
        }
    }
}
