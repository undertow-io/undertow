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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * {@link RemoteEndpoint} implementation which uses a WebSocketSession for all its operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class WebSocketSessionRemoteEndpoint implements RemoteEndpoint {

    private final UndertowSession undertowSession;
    private final Async async = new AsyncWebSocketSessionRemoteEndpoint();
    private final Basic basic = new BasicWebSocketSessionRemoteEndpoint();
    private final Encoding encoding;

    WebSocketSessionRemoteEndpoint(UndertowSession session, final Encoding encoding) {
        this.undertowSession = session;
        this.encoding = encoding;
    }

    public Async getAsync() {
        return async;
    }

    public Basic getBasic() {
        return basic;
    }

    @Override
    public void flushBatch() {
        // Do nothing
    }

    @Override
    public void setBatchingAllowed(final boolean allowed) throws IOException {

    }

    @Override
    public boolean getBatchingAllowed() {
        return false;
    }

    @Override
    public void sendPing(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        if (applicationData == null) {
            throw JsrWebSocketMessages.MESSAGES.messageInNull();
        }
        if (applicationData.remaining() > 125) {
            throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
        }
        undertowSession.getChannelHandlerContext().writeAndFlush(new PingWebSocketFrame(Unpooled.copiedBuffer(applicationData)));
    }

    @Override
    public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        if (applicationData == null) {
            throw JsrWebSocketMessages.MESSAGES.messageInNull();
        }
        if (applicationData.remaining() > 125) {
            throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
        }
        undertowSession.getChannelHandlerContext().writeAndFlush(new PongWebSocketFrame(Unpooled.copiedBuffer(applicationData)));
    }

    class AsyncWebSocketSessionRemoteEndpoint implements Async {

        private long sendTimeout = 0;

        @Override
        public long getSendTimeout() {
            return sendTimeout;
        }

        @Override
        public void setSendTimeout(final long timeoutmillis) {
            sendTimeout = timeoutmillis;
        }

        @Override
        public void sendText(final String text, final SendHandler handler) {
            if (handler == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if (text == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            undertowSession.getChannelHandlerContext().writeAndFlush(new TextWebSocketFrame(text))
                    .addListener(new SendHandlerAdapter(handler));
        }

        @Override
        public Future<Void> sendText(final String text) {
            if (text == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            return undertowSession.getChannelHandlerContext().writeAndFlush(new TextWebSocketFrame(text));
        }

        @Override
        public Future<Void> sendBinary(final ByteBuffer data) {
            if (data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            return undertowSession.getChannelHandlerContext().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data)));
        }

        @Override
        public void sendBinary(final ByteBuffer data, final SendHandler completion) {

            if (completion == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if (data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            undertowSession.getChannelHandlerContext().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data))).addListener(new SendHandlerAdapter(completion));
        }

        @Override
        public Future<Void> sendObject(final Object o) {
            if (o == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            final SendResultFuture future = new SendResultFuture();
            sendObjectImpl(o, future);
            return future;
        }

        @Override
        public void sendObject(final Object data, final SendHandler handler) {

            if (handler == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if (data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            sendObjectImpl(data, handler);
        }

        private void sendObjectImpl(final Object o, final SendHandler callback) {
            try {
                if (o instanceof String) {
                    sendText((String) o, callback);
                } else if (o instanceof byte[]) {
                    sendBinary(ByteBuffer.wrap((byte[]) o), callback);
                } else if (o instanceof ByteBuffer) {
                    sendBinary((ByteBuffer) o, callback);
                } else if (encoding.canEncodeText(o.getClass())) {
                    sendText(encoding.encodeText(o), callback);
                } else if (encoding.canEncodeBinary(o.getClass())) {
                    sendBinary(encoding.encodeBinary(o), callback);
                } else {
                    // TODO: Replace on bug is fixed
                    // https://issues.jboss.org/browse/LOGTOOL-64
                    throw new EncodeException(o, "No suitable encoder found");
                }
            } catch (Exception e) {
                callback.onResult(new SendResult(e));
            }
        }

        @Override
        public void setBatchingAllowed(final boolean allowed) throws IOException {
            //undertowSession.getWebSocketChannel().setRequireExplicitFlush(allowed);
        }

        @Override
        public boolean getBatchingAllowed() {
            //return undertowSession.getWebSocketChannel().isRequireExplicitFlush();
            return false;
        }

        @Override
        public void flushBatch() throws IOException {
            //undertowSession.getWebSocketChannel().flush();
        }

        @Override
        public void sendPing(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            if (applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (applicationData.remaining() > 125) {
                throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
            }
            undertowSession.getChannelHandlerContext().writeAndFlush(new PingWebSocketFrame(Unpooled.copiedBuffer(applicationData)));
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            if (applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (applicationData.remaining() > 125) {
                throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
            }
            undertowSession.getChannelHandlerContext().writeAndFlush(new PongWebSocketFrame(Unpooled.copiedBuffer(applicationData)));
        }
    }


    class BasicWebSocketSessionRemoteEndpoint implements Basic {

        boolean inTextFragment = false;
        boolean inBinaryFragment = false;

        public void assertNotInFragment() {
            if (inTextFragment || inBinaryFragment) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
        }

        @Override
        public void sendText(final String text) throws IOException {
            if (text == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            assertNotInFragment();
            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new TextWebSocketFrame(text)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void sendBinary(final ByteBuffer data) throws IOException {
            if (data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            assertNotInFragment();
            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data))).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
            data.clear(); //for some reason the TCK expects this, might as well just match the RI behaviour
        }

        @Override
        public void sendText(final String partialMessage, final boolean isLast) throws IOException {
            if (partialMessage == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (inBinaryFragment) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            inTextFragment = !isLast;

            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new TextWebSocketFrame(isLast, 0, partialMessage)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }

        }

        @Override
        public void sendBinary(final ByteBuffer partialByte, final boolean isLast) throws IOException {

            if (partialByte == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (inTextFragment) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            inBinaryFragment = !isLast;

            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new BinaryWebSocketFrame(isLast, 0, Unpooled.copiedBuffer(partialByte))).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }

            partialByte.clear();
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            assertNotInFragment();
            return new BinaryOutputStream(this);
        }

        @Override
        public Writer getSendWriter() throws IOException {
            assertNotInFragment();
            return new WebSocketWriter(this);
        }

        @Override
        public void sendObject(final Object data) throws IOException, EncodeException {
            if (data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            sendObjectImpl(data);
        }

        private void sendObjectImpl(final Object o) throws IOException, EncodeException {
            if (o instanceof String) {
                sendText((String) o);
            } else if (o instanceof byte[]) {
                sendBinary(ByteBuffer.wrap((byte[]) o));
            } else if (o instanceof ByteBuffer) {
                sendBinary((ByteBuffer) o);
            } else if (encoding.canEncodeText(o.getClass())) {
                sendText(encoding.encodeText(o));
            } else if (encoding.canEncodeBinary(o.getClass())) {
                sendBinary(encoding.encodeBinary(o));
            } else {
                // TODO: Replace on bug is fixed
                // https://issues.jboss.org/browse/LOGTOOL-64
                throw new EncodeException(o, "No suitable encoder found");
            }
        }

        @Override
        public void setBatchingAllowed(final boolean allowed) throws IOException {

        }

        @Override
        public boolean getBatchingAllowed() {
            return false;
        }

        @Override
        public void flushBatch() throws IOException {

        }

        @Override
        public void sendPing(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            if (applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (applicationData.remaining() > 125) {
                throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
            }
            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new PingWebSocketFrame(Unpooled.copiedBuffer(applicationData))).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            if (applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            if (applicationData.remaining() > 125) {
                throw JsrWebSocketMessages.MESSAGES.messageTooLarge(applicationData.remaining(), 125);
            }
            try {
                undertowSession.getChannelHandlerContext().writeAndFlush(new PongWebSocketFrame(Unpooled.copiedBuffer(applicationData))).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }
    }
}
