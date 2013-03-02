package io.undertow.websockets.jsr;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfiguration;

import io.undertow.servlet.api.InstanceFactory;

/**
 * @author Stuart Douglas
 */
public class ConfiguredServerEndpoint {

    private final ServerEndpointConfiguration endpointConfiguration;
    private final InstanceFactory<Endpoint> endpointFactory;

    public ConfiguredServerEndpoint(final ServerEndpointConfiguration endpointConfiguration, final InstanceFactory<Endpoint> endpointFactory) {
        this.endpointConfiguration = endpointConfiguration;
        this.endpointFactory = endpointFactory;
    }

    public ServerEndpointConfiguration getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public InstanceFactory<Endpoint> getEndpointFactory() {
        return endpointFactory;
    }
}
