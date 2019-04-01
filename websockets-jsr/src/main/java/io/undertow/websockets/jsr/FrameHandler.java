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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.undertow.websockets.jsr.util.ClassUtils;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class FrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final Endpoint endpoint;
    private final UndertowSession session;
    protected static final byte[] EMPTY = new byte[0];
    private final ConcurrentMap<FrameType, HandlerWrapper> handlers = new ConcurrentHashMap<>();
    private final Executor executor;
    private StringBuilder stringBuffer;
    private ByteArrayOutputStream binaryBuffer;
    private FrameType expectedContinuation;


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
            executor = new OrderedExecutor(session.getExecutor());
        } else {
            executor = session.getChannel().eventLoop();
        }

        this.executor = executor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        if (msg instanceof CloseWebSocketFrame) {
            onCloseFrame((CloseWebSocketFrame) msg);
        } else if (msg instanceof PongWebSocketFrame) {
            onPongMessage((PongWebSocketFrame) msg);
        } else if (msg instanceof TextWebSocketFrame) {
            onText(msg, ((TextWebSocketFrame) msg).text());
        } else if (msg instanceof BinaryWebSocketFrame) {
            onBinary(msg);
        }else if (msg instanceof ContinuationWebSocketFrame) {
            if(expectedContinuation == FrameType.BYTE) {
                onBinary(msg);
            } else if(expectedContinuation == FrameType.TEXT) {
                onText(msg, ((ContinuationWebSocketFrame)msg).text());
            }
        }
    }

    void onCloseFrame(final CloseWebSocketFrame message) {
        if (session.isSessionClosed()) {
            //we have already handled this when we sent the close frame
            return;
        }
        String reason = message.reasonText();
        int code = message.statusCode() == -1 ? CloseReason.CloseCodes.NORMAL_CLOSURE.getCode() : message.statusCode();

        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                try {
                    session.closeInternal(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
                } catch (IOException e) {
                    invokeOnError(e);
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

    private void onPongMessage(final PongWebSocketFrame frame) {
        if (session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            return;
        }
        final HandlerWrapper handler = getHandler(FrameType.PONG);
        if (handler != null) {
            final PongMessage message = DefaultPongMessage.create(Unpooled.copiedBuffer(frame.content()).nioBuffer());

            session.getContainer().invokeEndpointMethod(executor, new Runnable() {
                @Override
                public void run() {
                    try {
                        ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
                    } catch (Exception e) {
                        invokeOnError(e);
                    }
                }
            });
        }
    }

    private void onText(WebSocketFrame frame, String text) throws IOException {
        if (session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            session.close();
            return;
        }

        if(!frame.isFinalFragment()) {
            expectedContinuation = FrameType.TEXT;
        } else {
            expectedContinuation = null;
        }
        final HandlerWrapper handler = getHandler(FrameType.TEXT);
        if (handler != null &&
                (handler.isPartialHandler() || (stringBuffer == null && frame.isFinalFragment()))) {
            invokeTextHandler(text, handler, frame.isFinalFragment());
        } else if (handler != null) {
            if (stringBuffer == null) {
                stringBuffer = new StringBuilder();
            }
            stringBuffer.append(text);
            if (frame.isFinalFragment()) {
                invokeTextHandler(stringBuffer.toString(), handler, frame.isFinalFragment());
                stringBuffer = null;
            }
        }
    }

    private void onBinary(WebSocketFrame frame) throws IOException {
        if (session.isSessionClosed()) {
            //to bad, the channel has already been closed
            //we just ignore messages that are received after we have closed, as the endpoint is no longer in a valid state to deal with them
            //this this should only happen if a message was on the wire when we called close()
            session.close();
            return;
        }
        if(!frame.isFinalFragment()) {
            expectedContinuation = FrameType.BYTE;
        } else {
            expectedContinuation = null;
        }
        final HandlerWrapper handler = getHandler(FrameType.BYTE);
        if (handler != null &&
                (handler.isPartialHandler() || (binaryBuffer == null && frame.isFinalFragment()))) {
            byte[] data = new byte[frame.content().readableBytes()];
            frame.content().readBytes(data);
            invokeBinaryHandler(data, handler, frame.isFinalFragment());
        } else if (handler != null) {
            if (binaryBuffer == null) {
                binaryBuffer = new ByteArrayOutputStream();
            }
            byte[] data = new byte[frame.content().readableBytes()];
            frame.content().readBytes(data);
            binaryBuffer.write(data);
            if (frame.isFinalFragment()) {
                invokeBinaryHandler(binaryBuffer.toByteArray(), handler, frame.isFinalFragment());
                binaryBuffer = null;
            }
        }
    }

    private void invokeBinaryHandler(final byte[] data, final HandlerWrapper handler, final boolean finalFragment) {

        session.getContainer().invokeEndpointMethod(executor, new Runnable() {
            @Override
            public void run() {
                try {
                    if (handler.isPartialHandler()) {
                        MessageHandler.Partial mHandler = (MessageHandler.Partial) handler.getHandler();
                        if (handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), data);
                            mHandler.onMessage(object, finalFragment);
                        } else if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(ByteBuffer.wrap(data), finalFragment);
                        } else if (handler.getMessageType() == byte[].class) {
                            mHandler.onMessage(data, finalFragment);
                        } else if (handler.getMessageType() == InputStream.class) {
                            mHandler.onMessage(new ByteArrayInputStream(data), finalFragment);
                        }
                    } else {
                        MessageHandler.Whole mHandler = (MessageHandler.Whole) handler.getHandler();
                        if (handler.decodingNeeded) {
                            Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), data);
                            mHandler.onMessage(object);
                        } else if (handler.getMessageType() == ByteBuffer.class) {
                            mHandler.onMessage(ByteBuffer.wrap(data));
                        } else if (handler.getMessageType() == byte[].class) {
                            mHandler.onMessage(data);
                        } else if (handler.getMessageType() == InputStream.class) {
                            mHandler.onMessage(new ByteArrayInputStream(data));
                        }
                    }
                } catch (Exception e) {
                    invokeOnError(e);
                }
            }
        });
    }

    private void invokeTextHandler(final String message, final HandlerWrapper handler, final boolean finalFragment) {
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
                        if (handler.decodingNeeded) {
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
        for (HandlerWrapper handlerWrapper : handlerWrappers) {
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
     * <p>
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
        if (!ret.isEmpty()) {
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
            for (HandlerWrapper handlerWrapper : handlerWrappers) {
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
