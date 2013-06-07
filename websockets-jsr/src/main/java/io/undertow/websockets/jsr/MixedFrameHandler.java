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
import org.xnio.Buffers;

import javax.websocket.DecodeException;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
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
            String message = payload.toString();
            if (handler.getMessageType() == String.class) {
                ((MessageHandler.Whole) handler.getHandler()).onMessage(message);
            } else if (handler.getMessageType() == Reader.class) {
                ((MessageHandler.Whole) handler.getHandler()).onMessage(new StringReader(message));
            } else {
                try {
                    Object object = getSession().getEncoding().decodeText(handler.getMessageType(), message);
                    ((MessageHandler.Whole) handler.getHandler()).onMessage(object);
                } catch (DecodeException e) {
                    getEndpoint().onError(getSession(), e);
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
        if (handler.getHandler() instanceof MessageHandler.Partial) {
            super.onBinaryFrame(s, header, payload);
        } else {
            MessageHandler.Whole mHandler = (MessageHandler.Whole) handler.getHandler();
            if (handler.getMessageType() == ByteBuffer.class) {
                mHandler.onMessage(toBuffer(payload));
            } else if (handler.getMessageType() == byte[].class) {
                long size = Buffers.remaining(payload);
                if (size == 0) {
                    mHandler.onMessage(EMPTY);
                } else {
                    byte[] data = toArray(payload);
                    mHandler.onMessage(data);
                }
            } else if (handler.getMessageType() == InputStream.class) {
                long size = Buffers.remaining(payload);
                if (size == 0) {
                    mHandler.onMessage(new ByteArrayInputStream(EMPTY));
                } else {
                    byte[] data = toArray(payload);
                    mHandler.onMessage(new ByteArrayInputStream(data));
                }
            } else {
                try {
                    Object object = getSession().getEncoding().decodeBinary(handler.getMessageType(), toArray(payload));
                    mHandler.onMessage(object);
                } catch (DecodeException e) {
                    getEndpoint().onError(getSession(), e);
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
