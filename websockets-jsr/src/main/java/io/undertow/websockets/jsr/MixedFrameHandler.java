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

import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;

import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class MixedFrameHandler extends PartialFrameHandler {
    final List<ByteBuffer> textFrame = new ArrayList<ByteBuffer>();
    final List<ByteBuffer> binaryFrame = new ArrayList<ByteBuffer>();

    public MixedFrameHandler(UndertowSession session, Endpoint endpoint) {
        super(session, endpoint);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onTextFrame(WebSocketSession s, WebSocketFrameHeader header, ByteBuffer... payload) {
        HandlerWrapper handler = getHandler(FrameType.TEXT);
        if (handler == null) {
            return;
        }
        MessageHandler mHandler = handler.getHandler();
        if (mHandler instanceof MessageHandler.Partial) {
            super.onTextFrame(s, header, payload);
        } else {
            if (textFrame.isEmpty() && header.isLastFragement()) {
                ((MessageHandler.Whole) mHandler).onMessage(toString(payload));
            } else {
                for (ByteBuffer buf: payload) {
                    if (buf.hasRemaining()) {
                        textFrame.add(buf);
                    }
                }
                if (header.isLastFragement()) {
                    try {
                        ((MessageHandler.Whole) mHandler).onMessage(toString(textFrame.toArray(new ByteBuffer[0])));
                    } finally {
                        textFrame.clear();
                    }
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onBinaryFrame(WebSocketSession s, WebSocketFrameHeader header, ByteBuffer... payload) {
        HandlerWrapper handler = getHandler(FrameType.BYTE);
        if (handler == null) {
            return;
        }
        MessageHandler mHandler = handler.getHandler();
        if (mHandler instanceof MessageHandler.Partial) {
            super.onBinaryFrame(s, header, payload);
        } else {
            if (binaryFrame.isEmpty() && header.isLastFragement()) {
                ((MessageHandler.Whole) mHandler).onMessage(toBuffer(payload));
            } else {
                for (ByteBuffer buf: payload) {
                    if (buf.hasRemaining()) {
                        binaryFrame.add(buf);
                    }
                }
                if (header.isLastFragement()) {
                    try {
                        ((MessageHandler.Whole) mHandler).onMessage(toBuffer(binaryFrame.toArray(new ByteBuffer[0])));
                    } finally {
                        binaryFrame.clear();
                    }
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void onPongFrame(WebSocketSession s, ByteBuffer... payload) {
        HandlerWrapper handler = getHandler(FrameType.PONG);
        if (handler != null) {
            PongMessage message;
            if (payload.length == 1) {
                message =  DefaultPongMessage.create(payload[0]);
            } else {
                message = DefaultPongMessage.create(toBuffer(payload));
            }
            ((MessageHandler.Whole)handler.getHandler()).onMessage(message);
        }
    }
}
