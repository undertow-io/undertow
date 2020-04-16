package io.undertow.server;

import io.undertow.server.handlers.Cookie;

/**
 * Sets the <pre>secure</pre> attribute on all response cookies.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public enum SecureCookieCommitListener implements ResponseCommitListener {
    INSTANCE;

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
        for (Cookie cookie : exchange.responseCookies()) {
            cookie.setSecure(true);
        }
    }
}
