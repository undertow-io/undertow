package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * A cookie
 *
 * @author Stuart Douglas
 */
public class CookieAttribute implements ExchangeAttribute {

    private final String cookieName;

    public CookieAttribute(final String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        Cookie cookie = exchange.getRequestCookies().get(cookieName);
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setResponseCookie(new CookieImpl(cookieName, newValue));
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Cookie";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{c,") && token.endsWith("}")) {
                final String cookieName = token.substring(4, token.length() - 1);
                return new CookieAttribute(cookieName);
            }
            return null;
        }
    }
}
