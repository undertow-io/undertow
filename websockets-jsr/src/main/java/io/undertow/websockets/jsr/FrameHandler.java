/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import org.xnio.Pooled;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class FrameHandler extends AbstractReceiveListener {
    private final Endpoint endpoint;
    private final UndertowSession session;
    protected static final byte[] EMPTY = new byte[0];
    private final ConcurrentMap<FrameType, HandlerWrapper> handlers = new ConcurrentHashMap<>();
    private final Executor executor;

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

        final Executor executor;
        if (session.getContainer().isDispatchToWorker()) {
            executor = new OrderedExecutor(session.getWebSocketChannel().getWorker());
        } else {
            executor = session.getWebSocketChannel().getIoThread();
        }

        this.executor = executor;
    }

    @Override
    protected void onFullCloseMessage(final WebSocketChannel channel, final BufferedBinaryMessage message) {
        if(session.isSessionClosed()) {
            //we have already handled this when we sent the close frame
            message.getData().free();
            return;
        }
        final Pooled<ByteBuffer[]> pooled = message.getData();
        final ByteBuffer singleBuffer = toBuffer(pooled.getResource());
        final ByteBuffer toSend = singleBuffer.duplicate();
        //send the close immediatly
        WebSockets.sendClose(toSend, channel, null);

        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                try {
                    if (singleBuffer.remaining() > 1) {
                        final CloseReason.CloseCode code = CloseReason.CloseCodes.getCloseCode(singleBuffer.getShort());
                        final String reasonPhrase = singleBuffer.remaining() > 1 ? new UTF8Output(singleBuffer).extract() : null;
                        session.closeInternal(new CloseReason(code, reasonPhrase));
                    } else {
                        session.closeInternal(new CloseReason(CloseReason.CloseCodes.NO_STATUS_CODE, null));
                    }
                } catch (IOException e) {
                    invokeOnError(e);
                } finally {
                    pooled.close();
                }
            }
        });
    }

    private void invokeOnError(final Throwable e) {
        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                getEndpoint().onError(session, e);
            }
        });
    }

    @Override
    protected void onFullPongMessage(final WebSocketChannel webSocketChannel, BufferedBinaryMessage bufferedBinaryMessage) {
        if(session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            bufferedBinaryMessage.getData().free();
            return;
        }
        final HandlerWrapper handler = getHandler(FrameType.PONG);
        if (handler != null) {
            final Pooled<ByteBuffer[]> pooled = bufferedBinaryMessage.getData();
            final PongMessage message = DefaultPongMessage.create(toBuffer(pooled.getResource()));

            session.getContainer().invokeEndpointMethod(executor, new Runnable() {
                @Override
                public void run() {
                    try {
                        ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
                    } catch (Exception e) {
                        invokeOnError(e);
                    } finally {
                        pooled.close();
                    }
                }
            });
        } else {
            bufferedBinaryMessage.getData().free();
        }
    }

    @Override
    protected void onText(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        if(session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            messageChannel.close();
            return;
        }
        final HandlerWrapper handler = getHandler(FrameType.TEXT);
        if (handler != null && handler.isPartialHandler()) {
            BufferedTextMessage data = new BufferedTextMessage(false);
            data.read(messageChannel, new WebSocketCallback<BufferedTextMessage>() {
                @Override
                public void complete(WebSocketChannel channel, BufferedTextMessage context) {
                    invokeTextHandler(context, handler, context.isComplete());
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
        if(session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            messageChannel.close();
            return;
        }
        final HandlerWrapper handler = getHandler(FrameType.BYTE);
        if (handler != null && handler.isPartialHandler()) {
            BufferedBinaryMessage data = new BufferedBinaryMessage(session.getMaxBinaryMessageBufferSize(), false);
            data.read(messageChannel, new WebSocketCallback<BufferedBinaryMessage>() {
                @Override
                public void complete(WebSocketChannel channel, BufferedBinaryMessage context) {
                    invokeBinaryHandler(context, handler, context.isComplete());
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

        final Pooled<ByteBuffer[]> pooled = context.getData();
        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                try {
                    if (handler.isPartialHandler()) {
                        MessageHandler.Partial mHandler = (MessageHandler.Partial) handler.getHandler();
                        ByteBuffer[] payload = pooled.getResource();
                        if(handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), toArray(payload));
                            mHandler.onMessage(object, finalFragment);
                        } else if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(toBuffer(payload), finalFragment);
                        } else if (handler.getMessageType() == byte[].class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(data, finalFragment);
                        } else if (handler.getMessageType() == InputStream.class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(new ByteArrayInputStream(data), finalFragment);
                        }
                    } else {
                        MessageHandler.Whole mHandler = (MessageHandler.Whole) handler.getHandler();
                        ByteBuffer[] payload = pooled.getResource();
                        if(handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), toArray(payload));
                            mHandler.onMessage(object);
                        } else if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(toBuffer(payload));
                        } else if (handler.getMessageType() == byte[].class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(data);
                        } else if (handler.getMessageType() == InputStream.class) {
                            byte[] data = toArray(payload);
                            mHandler.onMessage(new ByteArrayInputStream(data));
                        }
                    }
                } catch (Exception e) {
                    invokeOnError(e);
                } finally {
                    pooled.close();
                }
            }
        });
    }

    private void invokeTextHandler(final BufferedTextMessage data, final HandlerWrapper handler, final boolean finalFragment) {

        final String message = data.getData();
        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                MessageHandler mHandler = handler.getHandler();
                try {

                    if (mHandler instanceof MessageHandler.Partial) {
                        if (handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeText(handler.getMessageType(), message);
                            ((MessageHandler.Partial) handler.getHandler()).onMessage(object, finalFragment);
                        } else if (handler.getMessageType() == String.class) {
                            ((MessageHandler.Partial) handler.getHandler()).onMessage(message, finalFragment);
                        } else if (handler.getMessageType() == Reader.class) {
                            ((MessageHandler.Partial) handler.getHandler()).onMessage(new StringReader(message), finalFragment);
                        }
                    } else {
                        if(handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeText(handler.getMessageType(), message);
                            ((MessageHandler.Whole) handler.getHandler()).onMessage(object);
                        } else if (handler.getMessageType() == String.class) {
                            ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
                        } else if (handler.getMessageType() == Reader.class) {
                            ((MessageHandler.Whole) handler.getHandler()).onMessage(new StringReader(message));
                        }
                    }
                } catch (Exception e) {
                    invokeOnError(e);
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
        if(session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            return;
        }
        HandlerWrapper handler = getHandler(FrameType.TEXT);
        if (handler != null) {
            invokeTextHandler(message, handler, true);
        }
    }

    @Override
    protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
        if(session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            message.getData().close();
            return;
        }
        HandlerWrapper handler = getHandler(FrameType.BYTE);
        if (handler != null) {
            invokeBinaryHandler(message, handler, true);
        } else {
            message.getData().close();
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
            if (buf.hasArray()
                    && buf.arrayOffset() == 0
                    && buf.position() == 0
                    && buf.array().length == buf.remaining()) {
                return buf.array();
            }
        }
        return Buffers.take(payload, 0, payload.length);
    }

    public final void addHandler(Class<?> messageType, MessageHandler handler) {
        addHandlerInternal(handler, messageType, handler instanceof MessageHandler.Partial);
    }
    public final void addHandler(MessageHandler handler) {
        Map<Class<?>, Boolean> types = ClassUtils.getHandlerTypes(handler.getClass());
        for (Entry<Class<?>, Boolean> e : types.entrySet()) {
            Class<?> type = e.getKey();
            boolean partial = e.getValue();
            addHandlerInternal(handler, type, partial);
        }
    }

    private void addHandlerInternal(MessageHandler handler, Class<?> type, boolean partial) {
        verify(type, handler);

        List<HandlerWrapper> handlerWrappers = createHandlerWrappers(type, handler, partial);
        for(HandlerWrapper handlerWrapper : handlerWrappers) {
            if (handlers.containsKey(handlerWrapper.getFrameType())) {
                throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(handlerWrapper.getFrameType());
            } else {
                if (handlers.putIfAbsent(handlerWrapper.getFrameType(), handlerWrapper) != null) {
                    throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(handlerWrapper.getFrameType());
                }
            }
        }
    }

    /**
     * Return the {@link FrameType} for the given {@link Class}.
     *
     * Note that multiple wrappers can be returned if both text and binary frames can be decoded to the given type
     */
    protected List<HandlerWrapper> createHandlerWrappers(Class<?> type, MessageHandler handler, boolean partialHandler) {
        //check the encodings first
        Encoding encoding = session.getEncoding();
        List<HandlerWrapper> ret = new ArrayList<>(2);
        if (encoding.canDecodeText(type)) {
            ret.add(new HandlerWrapper(FrameType.TEXT, handler, type, true, false));
        }
        if (encoding.canDecodeBinary(type)) {
            ret.add(new HandlerWrapper(FrameType.BYTE, handler, type, true, false));
        }
        if(!ret.isEmpty()) {
            return ret;
        }
        if (partialHandler) {
            // Partial message handler supports only String, byte[] and ByteBuffer.
            // See JavaDocs of the MessageHandler.Partial interface.
            if (type == String.class) {
                return Collections.singletonList(new HandlerWrapper(FrameType.TEXT, handler, type, false, true));
            }
            if (type == byte[].class || type == ByteBuffer.class) {
                return Collections.singletonList(new HandlerWrapper(FrameType.BYTE, handler, type, false, true));
            }
            throw JsrWebSocketMessages.MESSAGES.unsupportedFrameType(type);
        }
        if (type == byte[].class || type == ByteBuffer.class || type == InputStream.class) {
            return Collections.singletonList(new HandlerWrapper(FrameType.BYTE, handler, type, false, false));
        }
        if (type == String.class || type == Reader.class) {
            return Collections.singletonList(new HandlerWrapper(FrameType.TEXT, handler, type, false, false));
        }
        if (type == PongMessage.class) {
            return Collections.singletonList(new HandlerWrapper(FrameType.PONG, handler, type, false, false));
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
        Map<Class<?>, Boolean> types = ClassUtils.getHandlerTypes(handler.getClass());
        for (Entry<Class<?>, Boolean> e : types.entrySet()) {
            Class<?> type = e.getKey();
            List<HandlerWrapper> handlerWrappers = createHandlerWrappers(type, handler, e.getValue());
            for(HandlerWrapper handlerWrapper : handlerWrappers) {
                FrameType frameType = handlerWrapper.getFrameType();
                HandlerWrapper wrapper = handlers.get(frameType);
                if (wrapper != null && wrapper.getMessageType() == type) {
                    handlers.remove(frameType, wrapper);
                }
            }
        }
    }

    /**
     * Return a safe copy of all registered {@link MessageHandler}s.
     */
    public final Set<MessageHandler> getHandlers() {
        Set<MessageHandler> msgHandlers = new HashSet<>();
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

        private HandlerWrapper(final FrameType frameType, MessageHandler handler, final Class<?> msgType, final boolean decodingNeeded, final boolean partialHandler) {
            this.frameType = frameType;
            this.handler = handler;

            this.msgType = msgType;
            this.decodingNeeded = decodingNeeded;
            this.partialHandler = partialHandler;
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

    public Executor getExecutor() {
        return executor;
    }

    UndertowSession getSession() {
        return session;
    }

    Endpoint getEndpoint() {
        return endpoint;
    }
}
