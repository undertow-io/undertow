package io.undertow.websockets.jsr.annotated;

import java.util.HashMap;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;

import io.undertow.servlet.api.InstanceHandle;

/**
 * @author Stuart Douglas
 */
public class AnnotatedEndpoint extends Endpoint {

    private final InstanceHandle<Object> instance;

    private final BoundMethod webSocketOpen;
    private final BoundMethod webSocketClose;
    private final BoundMethod webSocketError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryByteArrayMessage;
    private final BoundMethod binaryByteBufferMessage;
    private final BoundMethod pongMessage;

    AnnotatedEndpoint(final InstanceHandle<Object> instance, final BoundMethod webSocketOpen, final BoundMethod webSocketClose, final BoundMethod webSocketError, final BoundMethod textMessage, final BoundMethod binaryByteArrayMessage, final BoundMethod binaryByteBufferMessage, final BoundMethod pongMessage) {
        this.instance = instance;
        this.webSocketOpen = webSocketOpen;
        this.webSocketClose = webSocketClose;
        this.webSocketError = webSocketError;
        this.textMessage = textMessage;
        this.binaryByteArrayMessage = binaryByteArrayMessage;
        this.binaryByteBufferMessage = binaryByteBufferMessage;
        this.pongMessage = pongMessage;
    }

    @Override
    public void onOpen(final Session session, final EndpointConfiguration endpointConfiguration) {

        if (webSocketOpen != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(EndpointConfiguration.class, endpointConfiguration);
            params.put(Map.class, session.getPathParameters());
            webSocketOpen.invoke(instance.getInstance(), params);
        }


    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        if (webSocketClose != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            webSocketClose.invoke(instance.getInstance(), params);
        }
    }

    @Override
    public void onError(final Session session, final Throwable thr) {
        if (webSocketError != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Throwable.class, thr);
            params.put(Map.class, session.getPathParameters());
            webSocketError.invoke(instance.getInstance(), params);
        }
    }
}
