package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The ident username, not used, included for apache access log compatibility
 *
 * @author Stuart Douglas
 */
public class IdentUsernameAttribute implements ExchangeAttribute {

    public static final String IDENT_USERNAME = "%l";

    public static final ExchangeAttribute INSTANCE = new IdentUsernameAttribute();

    private IdentUsernameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return null;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Ident username", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Ident Username";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(IDENT_USERNAME)) {
                return IdentUsernameAttribute.INSTANCE;
            }
            return null;
        }
    }
}
