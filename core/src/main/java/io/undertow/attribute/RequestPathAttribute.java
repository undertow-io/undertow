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
import io.undertow.util.QueryParameterUtils;

/**
 * @author Stuart Douglas
 */
public class RequestPathAttribute implements ExchangeAttribute {

    public static final String REQUEST_PATH = "%{REQUEST_PATH}";

    public static final ExchangeAttribute INSTANCE = new RequestPathAttribute();

    private RequestPathAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRelativePath();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        int pos = newValue.indexOf('?');
        exchange.setResolvedPath("");
        if (pos == -1) {
            exchange.setRelativePath(newValue);
            exchange.setRequestURI(newValue);
            exchange.setRequestPath(newValue);
        } else {
            final String path = newValue.substring(0, pos);
            exchange.setRequestPath(path);
            exchange.setRelativePath(path);
            exchange.setRequestURI(newValue);

            final String newQueryString = newValue.substring(pos);
            exchange.setQueryString(newQueryString);
            exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(newQueryString.substring(1), QueryParameterUtils.getQueryParamEncoding(exchange)));
        }
    }

    @Override
    public String toString() {
        return REQUEST_PATH;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request Path";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(REQUEST_PATH) ? INSTANCE : null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}