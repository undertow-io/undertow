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

import java.util.concurrent.TimeUnit;

/**
 * The response time
 *
 * This will only work if {@link io.undertow.UndertowOptions#RECORD_REQUEST_START_TIME} has been set
 */
public class ResponseTimeAttribute implements ExchangeAttribute {

    public static final String RESPONSE_TIME_MILLIS_SHORT = "%D";
    public static final String RESPONSE_TIME_SECONDS_SHORT = "%T";
    public static final String RESPONSE_TIME_MILLIS = "%{RESPONSE_TIME}";
    public static final String RESPONSE_TIME_NANOS = "%{RESPONSE_TIME_NANOS}";

    private final TimeUnit timeUnit;

    public ResponseTimeAttribute(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        long requestStartTime = exchange.getRequestStartTime();
        if(requestStartTime == -1) {
            return null;
        }
        final long nanos = System.nanoTime() - requestStartTime;
        if(timeUnit == TimeUnit.SECONDS) {
            StringBuilder buf = new StringBuilder();
            long milis = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
            buf.append(Long.toString(milis / 1000));
            buf.append('.');
            int remains = (int) (milis % 1000);
            buf.append(Long.toString(remains / 100));
            remains = remains % 100;
            buf.append(Long.toString(remains / 10));
            buf.append(Long.toString(remains % 10));
            return buf.toString();
        } else {
            return String.valueOf(timeUnit.convert(nanos, TimeUnit.NANOSECONDS));
        }
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Response Time", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response Time";
        }

        @Override
        public ExchangeAttribute build(String token) {
            if (token.equals(RESPONSE_TIME_MILLIS) || token.equals(RESPONSE_TIME_MILLIS_SHORT)) {
                return new ResponseTimeAttribute(TimeUnit.MILLISECONDS);
            }
            if (token.equals(RESPONSE_TIME_SECONDS_SHORT)) {
                return new ResponseTimeAttribute(TimeUnit.SECONDS);
            }
            if(token.equals(RESPONSE_TIME_NANOS)) {
                return new ResponseTimeAttribute(TimeUnit.NANOSECONDS);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }

}
