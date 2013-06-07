package io.undertow.websockets.jsr.annotated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.PongMessage;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.api.FragmentedFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.jsr.DefaultPongMessage;
import io.undertow.websockets.jsr.JsrWebSocketMessages;
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
    public void onOpen(final Session session, final EndpointConfig endpointConfiguration) {

        UndertowSession s = (UndertowSession) session;
        s.setFrameHandler(new AnnotatedEndpointFrameHandler((UndertowSession) session));

        if (webSocketOpen != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(EndpointConfig.class, endpointConfiguration);
            params.put(Map.class, session.getPathParameters());
            invokeMethod(params, webSocketOpen, session);
        }

    }

    private void invokeMethod(final Map<Class<?>, Object> params, final BoundMethod method, final Session session) {
        try {
            method.invoke(instance.getInstance(), params);
        } catch (DecodeException e) {
            onError(session, e);
        }
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        if (webSocketClose != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            invokeMethod(params, webSocketClose, session);
        }
    }

    @Override
    public void onError(final Session session, final Throwable thr) {
        if (webSocketError != null) {
            final Map<Class<?>, Object> params = new HashMap<>();
            params.put(Session.class, session);
            params.put(Throwable.class, thr);
            params.put(Map.class, session.getPathParameters());
            try {
                webSocketError.invoke(instance.getInstance(), params);
            } catch (DecodeException e) {
                throw new RuntimeException(e); //not much we can do here
            }
        }
    }

    private class AnnotatedEndpointFrameHandler implements FragmentedFrameHandler {

        private final UndertowSession session;
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

        public AnnotatedEndpointFrameHandler(final UndertowSession session) {
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
                invokeMethod(params, webSocketClose, session);
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
                invokeMethod(params, pongMessage, session);
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
            invokeMethod(params, webSocketError, session);
        }

        @Override
        public void onTextFrame(final WebSocketSession s, final WebSocketFrameHeader header, final ByteBuffer... payload) {
            if (textMessage == null) {
                onError(s, JsrWebSocketMessages.MESSAGES.receivedTextFrameButNoMethod());
                return;
            }
            if (assembledTextFrame == null) {
                assembledTextFrame = new UTF8Output();
            }
            UTF8Output builder = assembledTextFrame;
            builder.write(payload);
            if (header.isLastFragement() || (textMessage.hasParameterType(boolean.class) && !textMessage.isDecoderRequired() && builder.hasData())) {
                Object messageObject;
                if (textMessage.isDecoderRequired()) {
                    try {
                        messageObject = session.getEncoding().decodeText(textMessage.getMessageType(), builder.extract());
                    } catch (DecodeException e) {
                        onError(s, e);
                        return;
                    }
                } else if (textMessage.getMessageType().equals(Reader.class)) {
                    messageObject = new StringReader(builder.extract());
                } else {
                    messageObject = builder.extract();
                }

                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                params.put(textMessage.getMessageType(), messageObject);
                params.put(boolean.class, header.isLastFragement());
                final Object result;
                try {
                    result = textMessage.invoke(instance.getInstance(), params);
                } catch (DecodeException e) {
                    onError(s, e);
                    return;
                } finally {
                    assembledTextFrame = null;
                }
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
                    session.getAsyncRemote().sendObject(result, errorReportingSendHandler);
                }
            }
        }

        @Override
        public void onBinaryFrame(final WebSocketSession s, final WebSocketFrameHeader header, final ByteBuffer... payload) {
            //TODO: this could be more efficent
            if (binaryMessage == null) {
                onError(s, JsrWebSocketMessages.MESSAGES.receivedBinaryFrameButNoMethod());
                return;
            }
            boolean allowPartial = binaryMessage.hasParameterType(boolean.class);
            //if they take a byte buffer and allow partial frames this is the most efficent path
            //we can also take this path for a non-fragmented frame with a single buffer in the payload
            if (binaryMessage.getMessageType() == ByteBuffer.class && (allowPartial || (payload.length == 1 && header.isLastFragement() && assembledBinaryFrame == null))) {
                final Map<Class<?>, Object> params = new HashMap<>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                Object result = null;
                for (int i = 0; i < payload.length; ++i) {

                    params.put(ByteBuffer.class, payload[i]);
                    params.put(boolean.class, header.isLastFragement() && i == payload.length - 1);
                    try {
                        result = binaryMessage.invoke(instance.getInstance(), params);
                    } catch (DecodeException e) {
                        onError(s, e);
                        return;
                    }
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
                if (header.isLastFragement() || binaryMessage.hasParameterType(boolean.class)) {
                    final Map<Class<?>, Object> params = new HashMap<>();
                    params.put(Session.class, session);
                    params.put(Map.class, session.getPathParameters());
                    if (binaryMessage.isDecoderRequired()) {
                        try {
                            params.put(binaryMessage.getMessageType(), session.getEncoding().decodeBinary(binaryMessage.getMessageType(), assembledBinaryFrame.toByteArray()));
                        } catch (DecodeException e) {
                            onError(s, e);
                            return;
                        }
                    } else if (binaryMessage.getMessageType() == ByteBuffer.class) {
                        params.put(ByteBuffer.class, ByteBuffer.wrap(assembledBinaryFrame.toByteArray()));
                    } else if (binaryMessage.getMessageType() == byte[].class) {
                        params.put(byte[].class, assembledBinaryFrame.toByteArray());
                    } else if (binaryMessage.getMessageType() == InputStream.class) {
                        params.put(InputStream.class, new ByteArrayInputStream(assembledBinaryFrame.toByteArray()));
                    } else {
                        //decoders
                        throw new RuntimeException("decoders are not implemented yet");
                    }
                    params.put(boolean.class, header.isLastFragement());
                    final Object result;
                    try {
                        result = binaryMessage.invoke(instance.getInstance(), params);
                    } catch (DecodeException e) {
                        onError(s, e);
                        return;
                    } finally {
                        assembledBinaryFrame = null;
                    }
                    sendResult(result);
                }
            }
        }
    }
}
