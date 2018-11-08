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

/**
 * Exchange attribute that represents a combination of attributes that should be merged into a single string.
 * Exchange属性-表示应合并为单个字符串的属性组合
 * @author Stuart Douglas
 */
public class CompositeExchangeAttribute implements ExchangeAttribute {

    private final ExchangeAttribute[] attributes;

    public CompositeExchangeAttribute(ExchangeAttribute[] attributes) {
        ExchangeAttribute[] copy = new ExchangeAttribute[attributes.length];
        System.arraycopy(attributes, 0, copy, 0, attributes.length);
        this.attributes = copy;
    }

    /**
     * 将各种属性添加成一个字符串
     * @param exchange The exchange
     * @return
     */
    @Override
    public String readAttribute(HttpServerExchange exchange) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attributes.length; ++i) {
            final String val = attributes[i].readAttribute(exchange);
            if(val != null) {
                sb.append(val);
            }
        }
        return sb.toString();
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("combined", newValue);
    }
}
