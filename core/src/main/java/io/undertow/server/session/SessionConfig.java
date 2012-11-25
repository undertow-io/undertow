package io.undertow.server.session;

import io.undertow.server.HttpServerExchange;

/**
 * Interface that abstracts the process of attaching a session to an exchange.
 *
 * Generally this will just set a cookie.
 *
 * @author Stuart Douglas
 */
public interface SessionConfig {


    void attachSession(final HttpServerExchange exchange, final Session session);

    void clearSession(final HttpServerExchange exchange, final Session session);

    String findSession(final HttpServerExchange exchange);

    String rewriteUrl(final String originalUrl, final Session session);

}
