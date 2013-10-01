package io.undertow.websockets.jsr.annotated;

import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.jsr.DefaultPongMessage;
import io.undertow.websockets.jsr.UndertowSession;
import org.xnio.Buffers;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.PongMessage;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

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
        boolean partialText = textMessage == null || (textMessage.hasParameterType(boolean.class) && !textMessage.getMessageType().equals(boolean.class));
        boolean partialBinary = binaryMessage == null || (binaryMessage.hasParameterType(boolean.class) && !binaryMessage.getMessageType().equals(boolean.class));
        s.setReceiveListener(new AnnotatedEndpointFrameHandler((UndertowSession) session, partialText, partialBinary));

        if (webSocketOpen != null) {
            final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
            params.put(Session.class, session);
            params.put(EndpointConfig.class, endpointConfiguration);
            params.put(Map.class, session.getPathParameters());
            invokeMethod(params, webSocketOpen, s);
        }

    }

    private void invokeMethod(final Map<Class<?>, Object> params, final BoundMethod method, final UndertowSession session) {
        session.getContainer().invokeEndpointMethod(session.getWebSocketChannel(), new Runnable() {
            @Override
            public void run() {
                try {
                    method.invoke(instance.getInstance(), params);
                } catch (DecodeException e) {
                    onError(session, e);
                }
            }
        });
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        if (webSocketClose != null) {
            final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            invokeMethod(params, webSocketClose, (UndertowSession) session);
        }
    }

    @Override
    public void onError(final Session session, final Throwable thr) {
        try {
            if (webSocketError != null) {
                final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
                params.put(Session.class, session);
                params.put(Throwable.class, thr);
                params.put(Map.class, session.getPathParameters());
                ((UndertowSession) session).getContainer().invokeEndpointMethod(((UndertowSession) session).getWebSocketChannel(), new Runnable() {
                    @Override
                    public void run() {
                        try {
                            webSocketError.invoke(instance.getInstance(), params);
                        } catch (DecodeException e) {
                            throw new RuntimeException(e); //not much we can do here
                        }
                    }
                });
            }
        } finally {
            ((UndertowSession) session).forceClose();
        }
    }

    private class AnnotatedEndpointFrameHandler extends AbstractReceiveListener {

        //because fragmented messages can be split on code points we may need
        //to buffer data between frames
        BufferedTextMessage bufferedTextMessage;
        private final UndertowSession session;
        private final boolean partialText;
        private final boolean partialBinary;
        private final SendHandler errorReportingSendHandler = new SendHandler() {
            @Override
            public void onResult(final SendResult result) {
                if (!result.isOK()) {
                    AnnotatedEndpoint.this.onError(null, result.getException());
                }
            }
        };

        public AnnotatedEndpointFrameHandler(final UndertowSession session, boolean partialText, boolean partialBinary) {
            this.session = session;
            this.partialText = partialText;
            this.partialBinary = partialBinary;
        }

        @Override
        protected long getMaxTextBufferSize() {
            if (textMessage != null) {
                return textMessage.getMaxMessageSize();
            }
            //TODO: what do we do when there is no handler?
            return 1;
        }

        @Override
        protected long getMaxBinaryBufferSize() {
            if (binaryMessage != null) {
                return binaryMessage.getMaxMessageSize();
            }
            return 1;
        }

        @Override
        protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
            super.onFullCloseMessage(channel, message);
            if (webSocketClose == null) {
                return;
            }
            try {
                final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
                params.put(Session.class, session);
                params.put(Map.class, session.getPathParameters());
                invokeMethod(params, webSocketClose, session);
            } catch (Exception e) {
                AnnotatedEndpoint.this.onError(session, e);
            }
        }

        @Override
        protected void onFullPongMessage(WebSocketChannel channel, BufferedBinaryMessage data) throws IOException {
            if (pongMessage == null) {
                return;
            }
            PongMessage message = DefaultPongMessage.create(WebSockets.mergeBuffers(data.getData()));
            final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            params.put(PongMessage.class, message);
            session.getContainer().invokeEndpointMethod(session.getWebSocketChannel(), new Runnable() {
                @Override
                public void run() {
                    final Object result;
                    try {
                        result = pongMessage.invoke(instance.getInstance(), params);
                    } catch (Exception e) {
                        AnnotatedEndpoint.this.onError(session, e);
                        return;
                    }
                    sendResult(result);
                }
            });
        }

        @Override
        protected void onError(WebSocketChannel channel, Throwable error) {
            AnnotatedEndpoint.this.onError(session, error);
        }

        @Override
        protected void onText(final WebSocketChannel webSocketChannel, final StreamSourceFrameChannel messageChannel) throws IOException {
            if (!partialText) {
                super.onText(webSocketChannel, messageChannel);
            } else {
                if (bufferedTextMessage == null) {
                    bufferedTextMessage = new BufferedTextMessage();
                }
                bufferedTextMessage.read(messageChannel, new WebSocketCallback<BufferedTextMessage>() {
                    @Override
                    public void complete(WebSocketChannel channel, BufferedTextMessage context) {
                        try {
                            handleTextMessage(context, messageChannel.isFinalFragment());
                        } finally {
                            if (messageChannel.isFinalFragment()) {
                                bufferedTextMessage = null;
                            }
                        }
                    }

                    @Override
                    public void onError(WebSocketChannel channel, BufferedTextMessage context, Throwable throwable) {
                        AnnotatedEndpoint.this.onError(session, throwable);
                        bufferedTextMessage = null;
                    }
                });
            }
        }

        @Override
        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
            handleTextMessage(message, true);
        }


        private void handleTextMessage(BufferedTextMessage message, boolean finalFragment) {
            final String data = message.getData();
            Object messageObject;

            if (textMessage.isDecoderRequired()) {
                try {
                    messageObject = session.getEncoding().decodeText(textMessage.getMessageType(), data);
                } catch (DecodeException e) {
                    AnnotatedEndpoint.this.onError(session, e);
                    return;
                }
            } else if (textMessage.getMessageType().equals(Reader.class)) {
                messageObject = new StringReader(data);
            } else {
                messageObject = data;
            }

            final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            params.put(textMessage.getMessageType(), messageObject);
            params.put(boolean.class, finalFragment);
            session.getContainer().invokeEndpointMethod(session.getWebSocketChannel(), new Runnable() {
                @Override
                public void run() {
                    final Object result;
                    try {
                        result = textMessage.invoke(instance.getInstance(), params);
                    } catch (Exception e) {
                        AnnotatedEndpoint.this.onError(session, e);
                        return;
                    }
                    sendResult(result);
                }
            });
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
        protected void onBinary(WebSocketChannel webSocketChannel, final StreamSourceFrameChannel messageChannel) throws IOException {
            if (!partialBinary) {
                super.onText(webSocketChannel, messageChannel);
            } else {
                BufferedBinaryMessage buffered = new BufferedBinaryMessage(session.getMaxBinaryMessageBufferSize());
                buffered.read(messageChannel, new WebSocketCallback<BufferedBinaryMessage>() {
                    @Override
                    public void complete(WebSocketChannel channel, BufferedBinaryMessage context) {
                        handleBinaryMessage(context, messageChannel.isFinalFragment());
                    }

                    @Override
                    public void onError(WebSocketChannel channel, BufferedBinaryMessage context, Throwable throwable) {
                        AnnotatedEndpoint.this.onError(session, throwable);
                    }
                });
            }
        }

        @Override
        protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
            handleBinaryMessage(message, true);
        }

        protected byte[] toArray(ByteBuffer... payload) {
            if (payload.length == 1) {
                ByteBuffer buf = payload[0];
                if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0) {
                    return buf.array();
                }
            }
            int size = (int) Buffers.remaining(payload);
            byte[] data = new byte[size];
            int pos = 0;
            for (ByteBuffer buf : payload) {
                int toWrite = buf.remaining();
                buf.get(data, pos, toWrite);
                pos += toWrite;
            }
            return data;
        }


        private void handleBinaryMessage(BufferedBinaryMessage message, boolean finalFragment) {

            final Map<Class<?>, Object> params = new HashMap<Class<?>, Object>();
            params.put(Session.class, session);
            params.put(Map.class, session.getPathParameters());
            if (binaryMessage.isDecoderRequired()) {
                try {
                    params.put(binaryMessage.getMessageType(), session.getEncoding().decodeBinary(binaryMessage.getMessageType(), toArray(message.getData())));
                } catch (Exception e) {
                    AnnotatedEndpoint.this.onError(session, e);
                    return;
                }
            } else if (binaryMessage.getMessageType() == ByteBuffer.class) {
                params.put(ByteBuffer.class, WebSockets.mergeBuffers(message.getData()));
            } else if (binaryMessage.getMessageType() == byte[].class) {
                params.put(byte[].class, toArray(message.getData()));
            } else if (binaryMessage.getMessageType() == InputStream.class) {
                params.put(InputStream.class, new ByteArrayInputStream(toArray(message.getData())));
            } else {
                //decoders
                throw new RuntimeException("decoders are not implemented yet");
            }
            params.put(boolean.class, finalFragment);
            session.getContainer().invokeEndpointMethod(session.getWebSocketChannel(), new Runnable() {
                @Override
                public void run() {
                    final Object result;
                    try {
                        result = binaryMessage.invoke(instance.getInstance(), params);
                    } catch (Exception e) {
                        AnnotatedEndpoint.this.onError(session, e);
                        return;
                    }
                    sendResult(result);
                }
            });
        }
    }
}
