package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;

/**
 * Server side upgrade handler. This handler can inspect the request and modify the response.
 * <p/>
 * If the request does not meet this handlers requirements it should return false to allow
 * other upgrade handlers to inspect the request.
 * <p/>
 * If the request is invalid (e.g. security information is invalid) this should thrown an IoException.
 * if this occurs no further handlers will be tried.
 *
 * @author Stuart Douglas
 */
public interface HttpUpgradeHandshake {

    /**
     * Validates an upgrade request and returns any extra headers that should be added to the response.
     *
     * @param exchange the server exchange
     * @return <code>true</code> if the handshake is valid and should be upgraded. False if it is invalid
     * @throws IOException If the handshake is invalid
     */
    boolean handleUpgrade(final HttpServerExchange exchange) throws IOException;

}
