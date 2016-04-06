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
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * The current servlet name
 *
 * @author Stuart Douglas
 */
public class ServletNameAttribute implements ExchangeAttribute {

    public static final String SERVLET_NAME = "%{SERVLET_NAME}";

    public static final ExchangeAttribute INSTANCE = new ServletNameAttribute();
    public static final String NAME = "Servlet Name";

    private ServletNameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        return src.getCurrentServlet().getManagedServlet().getServletInfo().getName();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException(NAME, newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(SERVLET_NAME)? INSTANCE : null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
