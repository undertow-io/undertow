package io.undertow.attribute;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * The remote user
 *
 * @author Stuart Douglas
 */
public class RemoteUserAttribute implements ExchangeAttribute {

    public static final String REMOTE_USER_SHORT = "%u";
    public static final String REMOTE_USER = "%{REMOTE_USER}";

    public static final ExchangeAttribute INSTANCE = new RemoteUserAttribute();

    private RemoteUserAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        SecurityContext sc = exchange.getSecurityContext();
        if (sc == null || !sc.isAuthenticated()) {
            return null;
        }
        return sc.getAuthenticatedAccount().getPrincipal().getName();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Remote user", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Remote user";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REMOTE_USER) || token.equals(REMOTE_USER_SHORT)) {
                return RemoteUserAttribute.INSTANCE;
            }
            return null;
        }
    }
}
