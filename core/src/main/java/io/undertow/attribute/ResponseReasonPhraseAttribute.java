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
import io.undertow.util.StatusCodes;


/**
 * The request status code
 *
 * @author Stuart Douglas
 */
public class ResponseReasonPhraseAttribute implements ExchangeAttribute {

    public static final String RESPONSE_REASON_PHRASE = "%{RESPONSE_REASON_PHRASE}";

    public static final ExchangeAttribute INSTANCE = new ResponseReasonPhraseAttribute();

    private ResponseReasonPhraseAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return StatusCodes.getReason(exchange.getStatusCode());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setReasonPhrase(newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response reason phrase";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(RESPONSE_REASON_PHRASE)) {
                return ResponseReasonPhraseAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
