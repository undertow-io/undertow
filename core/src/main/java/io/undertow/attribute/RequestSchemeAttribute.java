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
 * The request scheme
 *
 *  类似这个一个完整的uri地址,其实是一个协议标准foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose
 * urn:example:animal:ferret:nose
 * @author Stuart Douglas
 */
public class RequestSchemeAttribute implements ExchangeAttribute {

    public static final String REQUEST_SCHEME = "%{SCHEME}";

    public static final ExchangeAttribute INSTANCE = new RequestSchemeAttribute();

    private RequestSchemeAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRequestScheme();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setRequestScheme(newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request scheme";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_SCHEME)) {
                return RequestSchemeAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
