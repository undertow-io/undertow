package io.undertow.websockets.jsr.annotated;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

/**
 * @author Stuart Douglas
 */
class EmptyEndpointConfig implements EndpointConfig {

    public static EmptyEndpointConfig INSTANCE = new EmptyEndpointConfig();

    private EmptyEndpointConfig() {

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
