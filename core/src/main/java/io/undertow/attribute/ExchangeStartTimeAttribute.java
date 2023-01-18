/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;

/**
 * Time when exchange started.
 * This will only work if {@link io.undertow.UndertowOptions#RECORD_REQUEST_START_TIME} has been set
 *
 * @author baranowb
 */
public class ExchangeStartTimeAttribute implements ExchangeAttribute {

    public static final String DATE_TIME_SHORT = "%e";
    public static final String DATE_TIME = "%{EXCHANGE_START_TIME}";
    public static final String CUSTOM_TIME = "%{exchange start time,";

    public static final ExchangeAttribute INSTANCE = new ExchangeStartTimeAttribute();

    private final String dateFormat;
    private final ThreadLocal<SimpleDateFormat> cachedFormat;

    private ExchangeStartTimeAttribute() {
        this.dateFormat = null;
        this.cachedFormat = null;
    }

    public ExchangeStartTimeAttribute(final String dateFormat) {
        this(dateFormat, null);
    }

    public ExchangeStartTimeAttribute(final String dateFormat, final String timezone) {
        this.dateFormat = dateFormat;
        this.cachedFormat = new ThreadLocal<SimpleDateFormat>() {
            @Override
            protected SimpleDateFormat initialValue() {
                final SimpleDateFormat format = new SimpleDateFormat(dateFormat);
                if(timezone != null) {
                    format.setTimeZone(TimeZone.getTimeZone(timezone));
                }
                return format;
            }
        };
    }
    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        final long requestStartTime = exchange.getRequestStartTime();
        if(requestStartTime == -1) {
            return null;
        }
        final long millis = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(System.nanoTime()-requestStartTime, TimeUnit.NANOSECONDS);
        final Date startDate = new Date(millis);
        if(dateFormat == null) {
            return DateUtils.toCommonLogFormat(startDate);
        } else {
            final SimpleDateFormat dateFormat = this.cachedFormat.get();
            return dateFormat.format(startDate);
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Exchange start time", newValue);
    }

    @Override
    public String toString() {
        if (dateFormat == null)
            return DATE_TIME;
        return CUSTOM_TIME + dateFormat + "}";
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Exchange start time";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(DATE_TIME) || token.equals(DATE_TIME_SHORT)) {
                return ExchangeStartTimeAttribute.INSTANCE;
            }
            if(token.startsWith(CUSTOM_TIME) && token.endsWith("}")) {
                return new ExchangeStartTimeAttribute(token.substring(CUSTOM_TIME.length(), token.length() - 1));
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
