package io.undertow.server.handlers.accesslog;

import io.undertow.server.HttpServerExchange;

/**
 * A token that just prints a constant value
 *
* @author Stuart Douglas
*/
final class ConstantAccessLogToken implements TokenHandler {

    private final String token;

    ConstantAccessLogToken(final String token) {
        this.token = token;
    }

    @Override
    public String generateMessage(final HttpServerExchange exchange) {
        return token;
    }
}
