package io.undertow.websockets.jsr;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class ServerEnpointConfigImpl implements ServerEndpointConfig{

    private final Class<?> endpointclass;
    private final String path;

    public ServerEnpointConfigImpl(Class<?> endpointclass, String path) {
        this.endpointclass = endpointclass;
        this.path = path;
    }

    @Override
    public Class<?> getEndpointClass() {
        return endpointclass;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public List<String> getSubprotocols() {
        return Collections.emptyList();
    }

    @Override
    public List<Extension> getExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Configurator getConfigurator() {
        return new Configurator();
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return Collections.emptyList();
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return Collections.emptyMap();
    }
}
