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
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * A cookie
 * cookie 参数操作
 * @author Stuart Douglas
 */
public class CookieAttribute implements ExchangeAttribute {

    private final String cookieName;

    public CookieAttribute(final String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        Cookie cookie = exchange.getRequestCookies().get(cookieName);
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setResponseCookie(new CookieImpl(cookieName, newValue));
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Cookie";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{c,") && token.endsWith("}")) {
                final String cookieName = token.substring(4, token.length() - 1);
                return new CookieAttribute(cookieName);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
