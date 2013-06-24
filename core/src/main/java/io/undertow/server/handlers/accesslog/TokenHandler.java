package io.undertow.server.handlers.accesslog;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public interface TokenHandler {

    /**
     * Generate a log message based on this token
     * @param exchange The http server exchange
     * @return The result to be appended to the access log
     */
    String generateMessage(final HttpServerExchange exchange);

    interface Factory {

        /**
         *
         * @param token The token
         * @return A new token handler, or null if this factory cannot handle the provided token
         */
        TokenHandler create(final String token);

    }

}
