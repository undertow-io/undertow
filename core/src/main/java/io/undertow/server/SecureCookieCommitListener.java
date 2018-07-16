package io.undertow.server;

import io.undertow.server.handlers.Cookie;

import java.util.Map;

/**
 * Sets the <pre>secure</pre> attribute on all response cookies.
 */
public enum SecureCookieCommitListener implements ResponseCommitListener {
    INSTANCE;

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getResponseCookiesInternal();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> cookie : exchange.getResponseCookies().entrySet()) {
                cookie.getValue().setSecure(true);
            }
        }
    }
}
