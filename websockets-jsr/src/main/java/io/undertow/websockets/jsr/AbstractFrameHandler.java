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

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.jsr.util.ClassUtils;
import org.xnio.Buffers;

/**
 * Abstract base class which can be used to map {@link MessageHandler}s into a {@link FrameHandler}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
abstract class AbstractFrameHandler<E extends MessageHandler> implements FrameHandler {
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

    protected AbstractFrameHandler(UndertowSession session, Endpoint endpoint) {
        this.session = session;
        this.endpoint = endpoint;
    }

    @Override
    public final void onPingFrame(WebSocketSession session, ByteBuffer... payload) {
        // NOOP
    }

    @Override
    public final void onCloseFrame(WebSocketSession s, final CloseReason reason) {
        try {
            if (reason == null) {
                session.close();
            } else {
                session.close(new javax.websocket.CloseReason(javax.websocket.CloseReason.CloseCodes.getCloseCode(reason.getStatusCode()), reason.getReasonText()));
            }
        } catch (Throwable e) {
            endpoint.onError(session, e);
        }
    }

    @Override
    public void onPongFrame(WebSocketSession session, ByteBuffer... payload) {
        HandlerWrapper handler = getHandler(FrameType.PONG);
        if (handler != null) {
            PongMessage message;
            if (payload.length == 1) {
                message = DefaultPongMessage.create(payload[0]);
            } else {
                message = DefaultPongMessage.create(toBuffer(payload));
            }
            ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
        }
    }

    @Override
    public final void onError(WebSocketSession s, Throwable cause) {
        endpoint.onError(session, cause);
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

    private static Class<?> type(MessageHandler handler, final Encoding encoding) {
        Class<?> typeClazz = ClassUtils.getHandlerType(handler.getClass());
        return typeClazz;
    }

    public final void addHandler(E handler) {
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
    protected HandlerWrapper createHandlerWrapper(Class<?> type, E handler) {
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
    protected void verify(Class<?> type, E handler) {
        // NOOP
    }

    public final void removeHandler(E handler) {
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

    static final class HandlerWrapper {
        private final FrameType frameType;
        private final MessageHandler handler;
        private final Class<?> msgType;
        private final boolean decodingNeeded;

        private HandlerWrapper(final FrameType frameType, MessageHandler handler, final Class<?> msgType, final boolean decodingNeeded) {
            this.frameType = frameType;
            this.handler = handler;

            this.msgType = msgType;
            this.decodingNeeded = decodingNeeded;
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
    }

    UndertowSession getSession() {
        return session;
    }

    Endpoint getEndpoint() {
        return endpoint;
    }
}
