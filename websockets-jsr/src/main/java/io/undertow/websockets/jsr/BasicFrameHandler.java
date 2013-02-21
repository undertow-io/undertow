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

import io.undertow.websockets.api.AssembledFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;

import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;

/**
 * {@link AbstractFrameHandler} subclass which will allow to use {@link MessageHandler.Basic} implementations
 * to operated on received fragements.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class BasicFrameHandler extends AbstractFrameHandler<MessageHandler.Basic<?>> implements AssembledFrameHandler {

    public BasicFrameHandler(UndertowSession session, Endpoint endpoint) {
        super(session, endpoint);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void onTextFrame(WebSocketSession s, WebSocketFrameHeader header, CharSequence payload) {
        HandlerWrapper handler =  getHandler(FrameType.TEXT);
        if (handler != null) {
            ((MessageHandler.Basic)handler.getHandler()).onMessage(payload.toString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void onBinaryFrame(WebSocketSession s, WebSocketFrameHeader header, ByteBuffer... payload) {
        HandlerWrapper handler =  getHandler(FrameType.BYTE);
        if (handler != null) {
            MessageHandler.Basic mHandler = (MessageHandler.Basic) handler.getHandler();
            if (handler.getMessageType() == ByteBuffer.class) {
                mHandler.onMessage(toBuffer(payload));
            }
            if (handler.getMessageType() == byte[].class) {
                int size = size(payload);
                if (size == 0) {
                    mHandler.onMessage(EMPTY);
                } else {
                    byte[] data = toArray(payload);
                    mHandler.onMessage(data);
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
            ((MessageHandler.Basic)handler.getHandler()).onMessage(message);
        }
    }
}
