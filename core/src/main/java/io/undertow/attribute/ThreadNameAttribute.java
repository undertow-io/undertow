package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The thread name
 *
 * @author Stuart Douglas
 */
public class ThreadNameAttribute implements ExchangeAttribute {

    public static final String THREAD_NAME_SHORT = "%I";
    public static final String THREAD_NAME = "%{THREAD_NAME}";

    public static final ExchangeAttribute INSTANCE = new ThreadNameAttribute();

    private ThreadNameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return Thread.currentThread().getName();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Thread name", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Thread name";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(THREAD_NAME) || token.equals(THREAD_NAME_SHORT)) {
                return ThreadNameAttribute.INSTANCE;
            }
            return null;
        }
    }
}
