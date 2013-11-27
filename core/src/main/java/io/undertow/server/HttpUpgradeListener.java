package io.undertow.server;

import org.xnio.StreamConnection;

/**
 * Listener that is used to perform a HTTP upgrade.
 *
 * @author Stuart Douglas
 */
public interface HttpUpgradeListener {

    /**
     * Method that is called once the upgrade is complete.
     *
     * @param streamConnection The connection that can be used to send or receive data
     * @param exchange
     */
    void handleUpgrade(final StreamConnection streamConnection, HttpServerExchange exchange);

}
