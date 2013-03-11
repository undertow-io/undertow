package io.undertow.websockets.jsr;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.ServerEndpointConfigurator;

/**
 * @author Stuart Douglas
 */
public class DefaultServerEndpointConfigurator extends ServerEndpointConfigurator {

    public DefaultServerEndpointConfigurator() {
        super();
    }

    @Override
    public String getNegotiatedSubprotocol(final List<String> supported, final List<String> requested) {
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(final List<Extension> installed, final List<Extension> requested) {
        return Collections.emptyList();
    }

    @Override
    public boolean checkOrigin(final String originHeaderValue) {
        return true;
    }

    @Override
    public boolean matchesURI(final String path, final URI requestUri, final Map<String, String> templateExpansion) {
        return requestUri.getPath().equals(path);
    }

    @Override
    public void modifyHandshake(final ServerEndpointConfiguration sec, final HandshakeRequest request, final HandshakeResponse response) {

    }
}
