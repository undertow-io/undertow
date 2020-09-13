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
 * The relative path
 *
 * @author Stuart Douglas
 */
public class RelativePathAttribute implements ExchangeAttribute {

    public static final String RELATIVE_PATH_SHORT = "%R";
    public static final String RELATIVE_PATH = "%{RELATIVE_PATH}";

    public static final ExchangeAttribute INSTANCE = new RelativePathAttribute();

    private RelativePathAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRelativePath();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        int pos = newValue.indexOf('?');
        if (pos == -1) {
            exchange.setRelativePath(newValue);
            String requestURI = exchange.getResolvedPath() + newValue;
            if(requestURI.contains("%")) {
                //as the request URI is supposed to be encoded we need to replace
                //percent characters with their encoded form, otherwise we can run into issues
                //where the percent will be taked to be a encoded character
                //TODO: should we fully encode this? It seems like it also has the potential to cause issues, and encoding the percent character is probably enough
                exchange.setRequestURI(requestURI.replaceAll("%", "%25"));
            } else {
                exchange.setRequestURI(requestURI);
            }
            exchange.setRequestPath(requestURI);
        } else {
            final String path = newValue.substring(0, pos);
            exchange.setRelativePath(path);
            String requestURI = exchange.getResolvedPath() + path;
            if(requestURI.contains("%")) {
                exchange.setRequestURI(requestURI.replaceAll("%", "%25"));
            } else {
                exchange.setRequestURI(requestURI);
            }
            exchange.setRequestPath(requestURI);

            final String newQueryString = newValue.substring(pos);
            exchange.setQueryString(newQueryString);
            exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(newQueryString.substring(1), QueryParameterUtils.getQueryParamEncoding(exchange)));
        }
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
