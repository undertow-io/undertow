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

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.URLUtils;
import org.xnio.OptionMap;

/**
 * The query string
 *
 * @author Stuart Douglas
 */
public class QueryStringAttribute implements ExchangeAttribute {

    public static final String QUERY_STRING_SHORT = "%q";
    public static final String QUERY_STRING = "%{QUERY_STRING}";
    public static final String BARE_QUERY_STRING = "%{BARE_QUERY_STRING}";

    public static final ExchangeAttribute INSTANCE = new QueryStringAttribute(true);
    public static final ExchangeAttribute BARE_INSTANCE = new QueryStringAttribute(false);

    private final boolean includeQuestionMark;

    private QueryStringAttribute(boolean includeQuestionMark) {
        this.includeQuestionMark = includeQuestionMark;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        String qs = exchange.getDecodedQueryString();
        if(qs.isEmpty() || !includeQuestionMark) {
            return qs;
        }
        return '?' + qs;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setQueryString(newValue);
        // also set the decoded query string, otherwise getQueryString and getDecodedQueryString will have inconsistencies
        // because setQueryString does not modify the stored decoded query string and thus the decoded query string will still be the old value
        OptionMap options = exchange.getConnection().getUndertowOptions();
        exchange.setDecodedQueryString(
            URLUtils.decode(
                newValue,
                options.get(UndertowOptions.URL_CHARSET, UndertowOptions.DEFAULT_URL_CHARSET),
                options.get(UndertowOptions.DECODE_SLASH, false),
                new StringBuilder()
            ));
    }

    @Override
    public String toString() {
        if(includeQuestionMark) {
            return QUERY_STRING;
        } else {
            return BARE_QUERY_STRING;
        }
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Query String";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(QUERY_STRING) || token.equals(QUERY_STRING_SHORT)) {
                return QueryStringAttribute.INSTANCE;
            } else if(token.equals(BARE_QUERY_STRING)) {
                return QueryStringAttribute.BARE_INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
