package io.undertow.server;

import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 * Sets the <pre>secure</pre> attribute on all response cookies.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public enum SecureCookieCommitListener implements ResponseCommitListener {
    INSTANCE;

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
        handleResponseCookies(exchange);
        handleCookiesSetViaHeaders(exchange);
    }

    private void handleResponseCookies(HttpServerExchange exchange) {
        for (Cookie cookie : exchange.responseCookies()) {
            cookie.setSecure(true);
        }
    }

    private void handleCookiesSetViaHeaders(HttpServerExchange exchange) {
        HeaderValues cookieHeaders = exchange.getResponseHeaders().get(Headers.SET_COOKIE);
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                String[] parts = cookieHeader.split("=", 2);
                String cookieName = parts[0];
                String cookieValue = (parts.length > 1) ? parts[1] : null;
                CookieImpl cookie = new CookieImpl(cookieName, cookieValue);
                cookie.setSecure(true);
                exchange.setResponseCookie(cookie);
            }
            exchange.getResponseHeaders().remove(Headers.SET_COOKIE);
        }
    }
}
