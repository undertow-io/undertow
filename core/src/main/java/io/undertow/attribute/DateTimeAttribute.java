package io.undertow.attribute;

import java.util.Date;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;

/**
 * The request status code
 *
 * @author Stuart Douglas
 */
public class DateTimeAttribute implements ExchangeAttribute {

    public static final String DATE_TIME_SHORT = "%t";
    public static final String DATE_TIME = "%{DATE_TIME}";

    public static final ExchangeAttribute INSTANCE = new DateTimeAttribute();

    private DateTimeAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return DateUtils.toCommonLogFormat(new Date());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Date time", newValue);
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
            return null;
        }
    }
}
