/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.jsr;

import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.UTF8Output;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.jsr.util.ClassUtils;
import org.xnio.Buffers;

import javax.websocket.DecodeException;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class FrameHandler extends AbstractReceiveListener {
    private final Endpoint endpoint;
    private final UndertowSession session;
    protected static final byte[] EMPTY = new byte[0];
    private final ConcurrentMap<FrameType, HandlerWrapper> handlers = new ConcurrentHashMap<FrameType, HandlerWrapper>();

    /**
     * Supported types of WebSocket frames for which a {@link MessageHandler} can be added.
     */
    enum FrameType {
        PONG,
        BYTE,
        TEXT
    }

    protected FrameHandler(UndertowSession session, Endpoint endpoint) {
        this.session = session;
        this.endpoint = endpoint;
    }

    @Override
    protected void onFullCloseMessage(WebSocketChannel channel, final BufferedBinaryMessage message) {
        ByteBuffer[] payload = message.getData();
        final ByteBuffer singleBuffer = toBuffer(payload);
        ByteBuffer toSend = singleBuffer.duplicate();
        WebSockets.sendClose(toSend, channel, null);

        session.getContainer().invokeEndpointMethod(new Runnable() {
            @Override
            public void run() {
                try {
                    if (singleBuffer.remaining() > 2) {
                        final int code = singleBuffer.getShort();
                        session.close(new javax.websocket.CloseReason(javax.websocket.CloseReason.CloseCodes.getCloseCode(code), new UTF8Output(singleBuffer).extract()));
                    } else {
                        session.close();
                    }
                } catch (IOException e) {
                    invokeOnError(e);
                } finally {
                    message.release();
                }
            }
        });
    }

    private void invokeOnError(final Throwable e) {
        session.getContainer().invokeEndpointMethod(new Runnable() {
            @Override
            public void run() {
                try {
                    getEndpoint().onError(session, e);
                } finally {
                    session.forceClose();
                }
            }
        });
    }

    @Override
    protected void onFullPongMessage(WebSocketChannel webSocketChannel, BufferedBinaryMessage data) {
        final HandlerWrapper handler = getHandler(FrameType.PONG);
        if (handler != null) {
            ByteBuffer[] payload = data.getData();
            final PongMessage message = DefaultPongMessage.create(toBuffer(payload));

            session.getContainer().invokeEndpointMethod(new Runnable() {
                @Override
                public void run() {
                    ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
                }
            });
        }
    }

    @Override
    protected void onText(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        final HandlerWrapper handler = getHandler(FrameType.TEXT);
        final boolean finalFragment = messageChannel.isFinalFragment();
        if (handler != null && handler.isPartialHandler()) {
            BufferedTextMessage data = new BufferedTextMessage();
            data.read(messageChannel, new WebSocketCallback<BufferedTextMessage>() {
                @Override
                public void complete(WebSocketChannel channel, BufferedTextMessage context) {
                    invokeTextHandler(context, handler, finalFragment);
                }

                @Override
                public void onError(WebSocketChannel channel, BufferedTextMessage context, Throwable throwable) {
                    invokeOnError(throwable);
                }
            });
        } else {
            bufferFullMessage(messageChannel);
        }
    }


    @Override
    protected void onBinary(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        final HandlerWrapper handler = getHandler(FrameType.BYTE);
        final boolean finalFragment = messageChannel.isFinalFragment();
        if (handler != null && handler.isPartialHandler()) {
            BufferedBinaryMessage data = new BufferedBinaryMessage(session.getMaxBinaryMessageBufferSize());
            data.read(messageChannel, new WebSocketCallback<BufferedBinaryMessage>() {
                @Override
                public void complete(WebSocketChannel channel, BufferedBinaryMessage context) {
                    invokeBinaryHandler(context, handler, finalFragment);
                }

                @Override
                public void onError(WebSocketChannel channel, BufferedBinaryMessage context, Throwable throwable) {
                    invokeOnError(throwable);
                }
            });
        } else {
            bufferFullMessage(messageChannel);
        }

    }

    private void invokeBinaryHandler(final BufferedBinaryMessage context, final HandlerWrapper handler, final boolean finalFragment) {

        session.getContainer().invokeEndpointMethod(new Runnable() {
            @Override
            public void run() {
                try {
                    if (handler.isPartialHandler()) {
                        MessageHandler.Partial mHandler = (MessageHandler.Partial) handler.getHandler();
                        ByteBuffer[] payload = context.getData();
                        if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(toBuffer(payload), finalFragment);
                        } else if (handler.getMessageType() == byte[].class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(data, finalFragment);
                        } else if (handler.getMessageType() == InputStream.class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(new ByteArrayInputStream(data), finalFragment);
                        } else {
                            try {
                                Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), toArray(payload));
                                mHandler.onMessage(object, finalFragment);
                            } catch (DecodeException e) {
                                invokeOnError(e);
                            }
                        }
                    } else {
                        MessageHandler.Whole mHandler = (MessageHandler.Whole) handler.getHandler();
                        ByteBuffer[] payload = context.getData();
                        if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(toBuffer(payload));
                        } else if (handler.getMessageType() == byte[].class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(data);
                        } else if (handler.getMessageType() == InputStream.class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(new ByteArrayInputStream(data));
                        } else {
                            try {
                                Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), toArray(payload));
                                mHandler.onMessage(object);
                            } catch (DecodeException e) {
                                invokeOnError(e);
                            }
                        }
                    }
                } finally {
                    context.release();
                }
            }
        });
    }

    private void invokeTextHandler(final BufferedTextMessage data, final HandlerWrapper handler, final boolean finalFragment) {

        session.getContainer().invokeEndpointMethod(new Runnable() {
            @Override
            public void run() {
                MessageHandler mHandler = handler.getHandler();
                final String message = data.getData();

                if (mHandler instanceof MessageHandler.Partial) {
                    if (handler.getMessageType() == String.class) {
                        ((MessageHandler.Partial) handler.getHandler()).onMessage(message, finalFragment);
                    } else if (handler.getMessageType() == Reader.class) {
                        ((MessageHandler.Partial) handler.getHandler()).onMessage(new StringReader(message), finalFragment);
                    } else {
                        try {
                            Object object = getSession().getEncoding().decodeText(handler.getMessageType(), message);
                            ((MessageHandler.Partial) handler.getHandler()).onMessage(object, finalFragment);
                        } catch (DecodeException e) {
                            invokeOnError(e);
                        }
                    }
                } else {
                    if (handler.getMessageType() == String.class) {
                        ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
                    } else if (handler.getMessageType() == Reader.class) {
                        ((MessageHandler.Whole) handler.getHandler()).onMessage(new StringReader(message));
                    } else {
                        try {
                            Object object = getSession().getEncoding().decodeText(handler.getMessageType(), message);
                            ((MessageHandler.Whole) handler.getHandler()).onMessage(object);
                        } catch (DecodeException e) {
                            invokeOnError(e);
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onError(WebSocketChannel channel, Throwable error) {
        try {
            getEndpoint().onError(session, error);
        } finally {
            session.forceClose();
        }
    }

    @Override
    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
        HandlerWrapper handler = getHandler(FrameType.TEXT);
        if (handler != null) {
            invokeTextHandler(message, handler, true);
        }
    }

    @Override
    protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
        HandlerWrapper handler = getHandler(FrameType.BYTE);
        if (handler != null) {
            invokeBinaryHandler(message, handler, true);
        }
    }

    protected static ByteBuffer toBuffer(ByteBuffer... payload) {
        if (payload.length == 1) {
            return payload[0];
        }
        int size = (int) Buffers.remaining(payload);
        if (size == 0) {
            return Buffers.EMPTY_BYTE_BUFFER;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (ByteBuffer buf : payload) {
            buffer.put(buf);
        }
        buffer.flip();
        return buffer;
    }

    protected static byte[] toArray(ByteBuffer... payload) {
        if (payload.length == 1) {
            ByteBuffer buf = payload[0];
            if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0) {
                return buf.array();
            }
        }
        int size = (int) Buffers.remaining(payload);
        byte[] data = new byte[size];
        for (ByteBuffer buf : payload) {
            buf.get(data);
        }
        return data;
    }

    public final void addHandler(MessageHandler handler) {
        Class<?> type = ClassUtils.getHandlerType(handler.getClass());
        verify(type, handler);


        HandlerWrapper handlerWrapper = createHandlerWrapper(type, handler);


        if (handlers.containsKey(handlerWrapper.getFrameType())) {
            throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(handlerWrapper.getFrameType());
        } else {
            if (handlers.putIfAbsent(handlerWrapper.getFrameType(), handlerWrapper) != null) {
                throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(handlerWrapper.getFrameType());
            }
        }
    }

    /**
     * Return the {@link FrameType} for the given {@link Class}.
     */
    protected HandlerWrapper createHandlerWrapper(Class<?> type, MessageHandler handler) {
        if (type == byte[].class || type == ByteBuffer.class || type == InputStream.class) {
            return new HandlerWrapper(FrameType.BYTE, handler, type, false);
        }
        if (type == String.class || type == Reader.class) {
            return new HandlerWrapper(FrameType.TEXT, handler, type, false);
        }
        if (type == PongMessage.class) {
            return new HandlerWrapper(FrameType.PONG, handler, type, false);
        }
        Encoding encoding = session.getEncoding();
        if (encoding.canDecodeText(type)) {
            return new HandlerWrapper(FrameType.TEXT, handler, type, true);
        } else if (encoding.canDecodeBinary(type)) {
            return new HandlerWrapper(FrameType.BYTE, handler, type, true);
        }
        throw JsrWebSocketMessages.MESSAGES.unsupportedFrameType(type);
    }

    /**
     * Sub-classes may override this to do validations. This method is called before the add operations is executed.
     */
    protected void verify(Class<?> type, MessageHandler handler) {
        // NOOP
    }

    public final void removeHandler(MessageHandler handler) {
        Class<?> type = ClassUtils.getHandlerType(handler.getClass());
        FrameType frameType = createHandlerWrapper(type, handler).getFrameType();
        HandlerWrapper wrapper = handlers.get(frameType);
        if (wrapper != null && wrapper.getMessageType() == type) {
            handlers.remove(frameType, wrapper);
        }
    }

    /**
     * Return a safe copy of all registered {@link MessageHandler}s.
     */
    public final Set<MessageHandler> getHandlers() {
        Set<MessageHandler> msgHandlers = new HashSet<MessageHandler>();
        for (HandlerWrapper handler : handlers.values()) {
            msgHandlers.add(handler.getHandler());
        }
        return msgHandlers;
    }

    /**
     * Return the {@link HandlerWrapper} for the given {@link FrameType} or {@code null} if non was registered for
     * the given {@link FrameType}.
     */
    protected final HandlerWrapper getHandler(FrameType type) {
        return handlers.get(type);
    }

    @Override
    protected long getMaxTextBufferSize() {
        return session.getMaxTextMessageBufferSize();
    }

    protected long getMaxBinaryBufferSize() {
        return session.getMaxBinaryMessageBufferSize();
    }

    static final class HandlerWrapper {
        private final FrameType frameType;
        private final MessageHandler handler;
        private final Class<?> msgType;
        private final boolean decodingNeeded;
        private final boolean partialHandler;

        private HandlerWrapper(final FrameType frameType, MessageHandler handler, final Class<?> msgType, final boolean decodingNeeded) {
            this.frameType = frameType;
            this.handler = handler;

            this.msgType = msgType;
            this.decodingNeeded = decodingNeeded;
            this.partialHandler = handler instanceof MessageHandler.Partial;
        }

        /**
         * Return the {@link MessageHandler} which is used.
         */
        public MessageHandler getHandler() {
            return handler;
        }

        /**
         * Return the {@link Class} of the arguments accepted by the {@link MessageHandler}.
         */
        public Class<?> getMessageType() {
            return msgType;
        }

        FrameType getFrameType() {
            return frameType;
        }

        boolean isDecodingNeeded() {
            return decodingNeeded;
        }

        boolean isPartialHandler() {
            return partialHandler;
        }

    }

    UndertowSession getSession() {
        return session;
    }

    Endpoint getEndpoint() {
        return endpoint;
    }
}
