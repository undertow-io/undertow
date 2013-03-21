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

import io.undertow.websockets.api.FragmentedFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;

import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * {@link AbstractFrameHandler} subclass which will allow to use {@link MessageHandler.Async} implementations
 * to operated on received fragments.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AsyncFrameHandler extends AbstractFrameHandler<MessageHandler> implements FragmentedFrameHandler {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private UTF8Output utf8Output;

    public AsyncFrameHandler(UndertowSession session, Endpoint endpoint) {
        super(session, endpoint);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onTextFrame(WebSocketSession s, WebSocketFrameHeader header, ByteBuffer... payload) {
        HandlerWrapper handler =  getHandler(FrameType.TEXT);
        if (handler != null) {

            String text;
            boolean last = header.isLastFragement();
            if (utf8Output == null && last) {
                text = toString(payload);
            } else {
                if (utf8Output == null) {
                    utf8Output = new UTF8Output(payload);
                } else {
                    utf8Output.write(payload);
                }
                text = utf8Output.extract();
                if (last) {
                    utf8Output = null;
                }
            }
            ((MessageHandler.Async) handler.getHandler()).onMessage(text, last);
        }
    }

    @Override
    protected void verify(Class<?> type, MessageHandler handler) {
        if (handler instanceof MessageHandler.Async && type == PongMessage.class) {
            throw JsrWebSocketMessages.MESSAGES.pongMessageNotSupported();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onBinaryFrame(WebSocketSession s, WebSocketFrameHeader header, ByteBuffer... payload) {
        HandlerWrapper handler =  getHandler(FrameType.BYTE);
        if (handler != null) {
            MessageHandler.Async mHandler = (MessageHandler.Async) handler.getHandler();
            if (handler.getMessageType() == ByteBuffer.class) {
                mHandler.onMessage(toBuffer(payload), header.isLastFragement());
            }
            if (handler.getMessageType() == byte[].class) {
                int size = size(payload);
                if (size == 0) {
                    mHandler.onMessage(EMPTY, header.isLastFragement());
                } else {
                    byte[] data = toArray(payload);
                    mHandler.onMessage(data, header.isLastFragement());
                }
            }
        }
    }

    protected static String toString(ByteBuffer... payload) {
        ByteBuffer buffer = toBuffer(payload);
        if (buffer.hasArray()) {
            return new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), UTF_8);
        } else {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return new String(data, UTF_8);
        }
    }

    /**
     * Utility class which allows to extract a UTF8 String from bytes respecting valid code-points
     */
    static final class UTF8Output {
        private static final int UTF8_ACCEPT = 0;

        private static final byte[] TYPES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
                7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
                8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
                8, 8, 8, 8, 8, 8 };

        private static final byte[] STATES = { 0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
                12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
                12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
                12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
                12, 12, 12, 12, 12, 12 };

        @SuppressWarnings("RedundantFieldInitialization")
        private int state = UTF8_ACCEPT;
        private int codep;

        private final StringBuilder stringBuilder;

        UTF8Output(ByteBuffer... payload) {
            stringBuilder = new StringBuilder(size(payload));
            write(payload);
        }

        public void write(ByteBuffer... bytes) {
            for (ByteBuffer buf: bytes) {
                while(buf.hasRemaining()) {
                    write(buf.get());
                }
            }
        }

        private void write(int b) {
            byte type = TYPES[b & 0xFF];

            codep = state != UTF8_ACCEPT ? b & 0x3f | codep << 6 : 0xff >> type & b;

            state = STATES[state + type];

            if (state == UTF8_ACCEPT) {
                stringBuilder.append((char) codep);
            }
        }

        /**
         * Extract a String holding the utf8 text
         */
        public String extract() {
            String text = stringBuilder.toString();
            stringBuilder.delete(0, stringBuilder.length());
            return text;
        }
    }
}
