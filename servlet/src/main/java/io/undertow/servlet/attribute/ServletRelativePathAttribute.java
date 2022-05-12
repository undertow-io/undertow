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
import io.undertow.attribute.RelativePathAttribute;
import io.undertow.attribute.RequestURLAttribute;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

import jakarta.servlet.RequestDispatcher;

/**
 * The relative path
 *
 * @author Stuart Douglas
 */
public class ServletRelativePathAttribute implements ExchangeAttribute {

    public static final String RELATIVE_PATH_SHORT = "%R";
    public static final String RELATIVE_PATH = "%{RELATIVE_PATH}";

    public static final ExchangeAttribute INSTANCE = new ServletRelativePathAttribute();

    private ServletRelativePathAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if(src == null) {
            return RequestURLAttribute.INSTANCE.readAttribute(exchange);
        }
        String path = (String) src.getServletRequest().getAttribute(RequestDispatcher.FORWARD_PATH_INFO);
        String sp = (String) src.getServletRequest().getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        if(path == null && sp == null) {
            return RequestURLAttribute.INSTANCE.readAttribute(exchange);
        }
        if(sp == null) {
            return path;
        } else if(path == null) {
            return sp;
        } else {
            return sp + path;
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        RelativePathAttribute.INSTANCE.writeAttribute(exchange, newValue);
    }

    @Override
    public String toString() {
        return RELATIVE_PATH;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Relative Path";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(RELATIVE_PATH) || token.equals(RELATIVE_PATH_SHORT) ? INSTANCE : null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
