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
import io.undertow.attribute.RequestURLAttribute;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

import javax.servlet.RequestDispatcher;

/**
 * The request URL
 *
 * @author Stuart Douglas
 */
public class ServletRequestURLAttribute implements ExchangeAttribute {

    public static final String REQUEST_URL_SHORT = "%U";
    public static final String REQUEST_URL = "%{REQUEST_URL}";

    public static final ExchangeAttribute INSTANCE = new ServletRequestURLAttribute();

    private ServletRequestURLAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (src == null) {
            return RequestURLAttribute.INSTANCE.readAttribute(exchange);
        }
        String uri = (String) src.getServletRequest().getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (uri != null) {
            return uri;
        }
        uri = (String) src.getServletRequest().getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uri != null) {
            return uri;
        }
        return RequestURLAttribute.INSTANCE.readAttribute(exchange);
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        RequestURLAttribute.INSTANCE.writeAttribute(exchange, newValue);
    }

    @Override
    public String toString() {
        return REQUEST_URL;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request URL";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_URL) || token.equals(REQUEST_URL_SHORT)) {
                return ServletRequestURLAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 1;
        }
    }
}
