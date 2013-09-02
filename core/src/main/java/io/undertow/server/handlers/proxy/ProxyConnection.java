package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientConnection;

/**
 * A connection to a backend proxy.
 *
 * @author Stuart Douglas
 */
public class ProxyConnection {

    private final ClientConnection connection;
    private final String targetPath;

    public ProxyConnection(ClientConnection connection, String targetPath) {
        this.connection = connection;
        this.targetPath = targetPath;
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public String getTargetPath() {
        return targetPath;
    }
}
