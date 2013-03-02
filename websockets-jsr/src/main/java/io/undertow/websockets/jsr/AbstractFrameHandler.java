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

import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.jsr.util.ClassUtils;
import org.xnio.Buffers;

import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        endpoint.onClose(session, new javax.websocket.CloseReason(new javax.websocket.CloseReason.CloseCode() {
            @Override
            public int getCode() {
                return reason.getStatusCode();
            }
        }, reason.getReasonText()));
        session.close0();
    }

    /**
     * Noop implementation. Sub-classes may override this.
     */
    @Override
    public void onPongFrame(WebSocketSession session, ByteBuffer... payload) {
        // NOOP
    }

    @Override
    public final void onError(WebSocketSession s, Throwable cause) {
        endpoint.onError(session, cause);
    }

    protected static ByteBuffer toBuffer(ByteBuffer... payload) {
        if (payload.length == 1) {
            return payload[0];
        }
        int size = size(payload);
        if (size == 0) {
            return Buffers.EMPTY_BYTE_BUFFER;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (ByteBuffer buf: payload) {
            buffer.put(buf);
        }
        buffer.flip();
        return buffer;
    }

    protected static int size(ByteBuffer... payload) {
        int size = 0;
        for (ByteBuffer buf: payload) {
            size += buf.remaining();
        }
        return size;
    }

    protected static byte[] toArray(ByteBuffer... payload) {
        if (payload.length == 1) {
            ByteBuffer buf = payload[0];
            if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0) {
                return buf.array();
            }
        }
        int size = size(payload);
        byte[] data = new byte[size];
        for (ByteBuffer buf: payload) {
            buf.get(data);
        }
        return data;
    }

    private static Class<?> type(MessageHandler handler) {
        Class<?> typeClazz = ClassUtils.getHandlerType(handler.getClass());
        if (typeClazz != String.class && typeClazz != byte[].class && typeClazz != ByteBuffer.class && typeClazz != PongMessage.class) {
            throw JsrWebSocketMessages.MESSAGES.unsupportedFrameType(typeClazz);
        }
        return typeClazz;
    }

    public final void addHandler(E handler) {
        Class<?> type = type(handler);
        verify(type, handler);
        FrameType frameType = getFrameType(type);

        if (handlers.containsKey(frameType)) {
            throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(frameType);
        } else {
            HandlerWrapper wrapper = new HandlerWrapper(handler);
            if (handlers.putIfAbsent(frameType, wrapper) != null) {
                throw JsrWebSocketMessages.MESSAGES.handlerAlreadyRegistered(frameType);
            }
        }
    }

    /**
     * Return the {@link FrameType} for the given {@link Class}.
     */
    protected static FrameType getFrameType(Class<?> type) {
        if (type == byte[].class || type == ByteBuffer.class) {
            return FrameType.BYTE;
        }
        if (type == String.class) {
            return FrameType.TEXT;
        }
        if (type == PongMessage.class) {
            return FrameType.PONG;
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
        Class<?> type = type(handler);
        FrameType frameType = getFrameType(type);
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
        for (HandlerWrapper handler: handlers.values()) {
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
        private final MessageHandler handler;
        private final Class<?> msgType;

        private HandlerWrapper(MessageHandler handler) {
            msgType = type(handler);
            this.handler = handler;

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
    }
}
