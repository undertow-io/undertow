package io.undertow.websockets.jsr.annotated;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.PongMessage;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.api.FragmentedFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.jsr.DefaultPongMessage;
import io.undertow.websockets.jsr.UTF8Output;
import io.undertow.websockets.jsr.UndertowSession;
import org.xnio.Buffers;

/**
 * @author Stuart Douglas
 */
public class AnnotatedEndpoint extends Endpoint {

    private final InstanceHandle<?> instance;

    private final BoundMethod webSocketOpen;
    private final BoundMethod webSocketClose;
    private final BoundMethod webSocketError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryMessage;
    private final BoundMethod pongMessage;

    AnnotatedEndpoint(final InstanceHandle<?> instance, final BoundMethod webSocketOpen, final BoundMethod webSocketClose, final BoundMethod webSocketError, final BoundMethod textMessage, final BoundMethod binaryMessage, final BoundMethod pongMessage) {
        this.instance = instance;
        this.webSocketOpen = webSocketOpen;
        this.webSocketClose = webSocketClose;
        this.webSocketError = webSocketError;
        this.textMessage = textMessage;
        this.binaryMessage = binaryMessage;
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
        UndertowSession s = (UndertowSession) session;
        s.setFrameHandler(new AnnotatedEndpointFrameHandler(session));

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

    private class AnnotatedEndpointFrameHandler implements FragmentedFrameHandler {

        private final Session session;
        private UTF8Output assembledTextFrame;
        private ByteArrayOutputStream assembledBinaryFrame;
        private final SendHandler errorReportingSendHandler = new SendHandler() {
            @Override
            public void onResult(final SendResult result) {
                if (!result.isOK()) {
                    onError(null, result.getException());
                }
            }
        };

        public AnnotatedEndpointFrameHandler(final Session session) {
            this.session = session;
        }

        @Override
        public void onCloseFrame(final WebSocketSession s, final io.undertow.websockets.api.CloseReason reason) {
            if (webSocketClose == null) {
                return;
            }
            try {
                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                webSocketClose.invoke(instance.getInstance(), params);
            } catch (Exception e) {
                onError(s, e);
            }
        }

        @Override
        public void onPingFrame(final WebSocketSession s, final ByteBuffer... payload) {
            //noop
        }

        @Override
        public void onPongFrame(final WebSocketSession s, final ByteBuffer... payload) {
            if (pongMessage == null) {
                return;
            }
            PongMessage message;
            if (payload.length == 1) {
                message = DefaultPongMessage.create(payload[0]);
            } else {
                int count = 0;
                for (ByteBuffer b : payload) {
                    count += b.remaining();
                }
                ByteBuffer data = ByteBuffer.allocate(count);
                Buffers.copy(data, payload, 0, payload.length);
                message = DefaultPongMessage.create(data);
            }
            try {
                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                params.put(PongMessage.class, message);
                pongMessage.invoke(instance.getInstance(), params);
            } catch (Exception e) {
                onError(s, e);
            }
        }

        @Override
        public void onError(final WebSocketSession s, final Throwable cause) {
            if (webSocketError == null) {
                return;
            }
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            params.put(Throwable.class, cause);
            webSocketError.invoke(instance.getInstance(), params);
        }

        @Override
        public void onTextFrame(final WebSocketSession s, final WebSocketFrameHeader header, final ByteBuffer... payload) {
            if (assembledTextFrame == null) {
                assembledTextFrame = new UTF8Output();
            }
            UTF8Output builder = assembledTextFrame;
            builder.write(payload);
            if (header.isLastFragement() || textMessage.hasParameterType(boolean.class)) {
                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                params.put(String.class, builder.extract());
                params.put(boolean.class, true);
                Object result = textMessage.invoke(instance.getInstance(), params);
                assembledTextFrame = null;
                sendResult(result);
            }
        }

        private void sendResult(final Object result) {
            if (result != null) {
                if (result instanceof String) {
                    session.getAsyncRemote().sendText((String) result, errorReportingSendHandler);
                } else if (result instanceof byte[]) {
                    session.getAsyncRemote().sendBinary(ByteBuffer.wrap((byte[]) result), errorReportingSendHandler);
                } else if (result instanceof ByteBuffer) {
                    session.getAsyncRemote().sendBinary((ByteBuffer) result, errorReportingSendHandler);
                } else {
                    //TODO: how do we send primities? text or binary
                    session.getAsyncRemote().sendObject(result, errorReportingSendHandler);
                }
            }
        }

        @Override
        public void onBinaryFrame(final WebSocketSession s, final WebSocketFrameHeader header, final ByteBuffer... payload) {
            //TODO: this could be more efficent

            boolean allowPartial = textMessage.hasParameterType(boolean.class);
            //if they take a byte buffer and allow partial frames this is the most efficent path
            //we can also take this path for a non-fragmented frame with a single buffer in the payload
            if (textMessage.hasParameterType(ByteBuffer.class) && (allowPartial || (payload.length == 1 && header.isLastFragement() && assembledBinaryFrame == null))) {
                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                Object result = null;
                for (int i = 0; i < payload.length; ++i) {

                    params.put(ByteBuffer.class, payload);
                    params.put(boolean.class, header.isLastFragement() && i == payload.length - 1);
                    result = textMessage.invoke(instance.getInstance(), params);
                    sendResult(result);
                }
            } else {
                if (assembledBinaryFrame == null) {
                    assembledBinaryFrame = new ByteArrayOutputStream();
                }
                ByteArrayOutputStream builder = assembledBinaryFrame;
                for (ByteBuffer buf : payload) {
                    while (buf.hasRemaining()) {
                        builder.write(buf.get());
                    }
                }
                if (header.isLastFragement() || textMessage.hasParameterType(boolean.class)) {
                    final Map<Class<?>, Object> params = new HashMap<>();
                    params.put(Session.class, session);
                    params.put(Map.class, session.getPathParameters());
                    if (binaryMessage.hasParameterType(ByteBuffer.class)) {
                        params.put(ByteBuffer.class, ByteBuffer.wrap(assembledBinaryFrame.toByteArray()));
                    } else if (binaryMessage.hasParameterType(byte[].class)) {
                        params.put(byte[].class, assembledBinaryFrame.toByteArray());
                    } else {
                        //decoders
                        throw new RuntimeException("decoders are not implemented yet");
                    }
                    params.put(boolean.class, header.isLastFragement());
                    Object result = binaryMessage.invoke(instance.getInstance(), params);
                    assembledBinaryFrame = null;
                    sendResult(result);
                }
            }
        }
    }
}
