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

import java.util.ArrayDeque;
import java.util.Deque;

import io.undertow.server.HttpServerExchange;

/**
 * Path parameter
 *
 * @author Stuart Douglas
 */
public class PathParameterAttribute implements ExchangeAttribute {


    private final String parameter;

    public PathParameterAttribute(String parameter) {
        this.parameter = parameter;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        Deque<String> res = exchange.getPathParameters().get(parameter);
        if(res == null) {
            return null;
        }else if(res.isEmpty()) {
            return "";
        } else if(res.size() ==1) {
            return res.getFirst();
        } else {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for(String s : res) {
                sb.append(s);
                if(++i != res.size()) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        final ArrayDeque<String> value = new ArrayDeque<>();
        value.add(newValue);
        exchange.getPathParameters().put(parameter, value);
    }

    @Override
    public String toString() {
        return "%{p," + parameter + "}";
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Path Parameter";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{p,") && token.endsWith("}")) {
                final String qp = token.substring(4, token.length() - 1);
                return new PathParameterAttribute(qp);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
