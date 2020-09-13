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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;

/**
 * The current time
 *
 * @author Stuart Douglas
 */
public class DateTimeAttribute implements ExchangeAttribute {

    public static final String DATE_TIME_SHORT = "%t";
    public static final String DATE_TIME = "%{DATE_TIME}";
    public static final String CUSTOM_TIME = "%{time,";

    public static final ExchangeAttribute INSTANCE = new DateTimeAttribute();

    private final String dateFormat;
    private final ThreadLocal<SimpleDateFormat> cachedFormat;

    private DateTimeAttribute() {
        this.dateFormat = null;
        this.cachedFormat = null;
    }

    public DateTimeAttribute(final String dateFormat) {
        this(dateFormat, null);
    }

    public DateTimeAttribute(final String dateFormat, final String timezone) {
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
        if(dateFormat == null) {
            return DateUtils.toCommonLogFormat(new Date());
        } else {
            final SimpleDateFormat dateFormat = this.cachedFormat.get();
            return dateFormat.format(new Date());
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Date time", newValue);
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
            return "Date Time";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(DATE_TIME) || token.equals(DATE_TIME_SHORT)) {
                return DateTimeAttribute.INSTANCE;
            }
            if(token.startsWith(CUSTOM_TIME) && token.endsWith("}")) {
                return new DateTimeAttribute(token.substring(CUSTOM_TIME.length(), token.length() - 1));
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
