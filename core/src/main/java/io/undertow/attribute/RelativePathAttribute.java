package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The relative path
 *
 * @author Stuart Douglas
 */
public class RelativePathAttribute implements ExchangeAttribute {

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
        exchange.setRelativePath(newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Relative Path";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(RELATIVE_PATH) ? INSTANCE : null;
        }
    }
}
