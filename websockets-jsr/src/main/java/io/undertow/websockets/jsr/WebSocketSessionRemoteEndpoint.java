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

import io.undertow.websockets.core.BinaryOutputStream;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.WebSockets;
import org.xnio.channels.Channels;

import javax.websocket.EncodeException;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

/**
 * {@link RemoteEndpoint} implementation which uses a WebSocketSession for all its operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class WebSocketSessionRemoteEndpoint implements RemoteEndpoint {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final WebSocketChannel webSocketChannel;
    private final EndpointConfig config;
    private final Async async = new AsyncWebSocketSessionRemoteEndpoint();
    private final Basic basic = new BasicWebSocketSessionRemoteEndpoint();
    private final Encoding encoding;

    public WebSocketSessionRemoteEndpoint(WebSocketChannel webSocketChannel, EndpointConfig config, final Encoding encoding) {
        this.webSocketChannel = webSocketChannel;
        this.config = config;
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
        WebSockets.sendPing(applicationData, webSocketChannel, null);
    }

    @Override
    public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        WebSockets.sendPong(applicationData, webSocketChannel, null);
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
            if(handler == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if(text == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            WebSockets.sendText(text, webSocketChannel, new SendHandlerAdapter(handler), sendTimeout);
        }

        @Override
        public Future<Void> sendText(final String text) {
            if(text == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            final SendResultFuture future = new SendResultFuture();
            WebSockets.sendText(text, webSocketChannel, future, sendTimeout);
            return future;
        }

        @Override
        public Future<Void> sendBinary(final ByteBuffer data) {
            if(data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            final SendResultFuture future = new SendResultFuture();
            WebSockets.sendBinary(data, webSocketChannel, future, sendTimeout);
            return future;
        }

        @Override
        public void sendBinary(final ByteBuffer data, final SendHandler completion) {

            if(completion == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if(data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            WebSockets.sendBinary(data, webSocketChannel, new SendHandlerAdapter(completion), sendTimeout);
        }

        @Override
        public Future<Void> sendObject(final Object o) {
            if(o == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            final SendResultFuture future = new SendResultFuture();
            sendObjectImpl(o, future);
            return future;
        }

        @Override
        public void sendObject(final Object data, final SendHandler handler) {

            if(handler == null) {
                throw JsrWebSocketMessages.MESSAGES.handlerIsNull();
            }
            if(data == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            sendObjectImpl(data, new SendHandlerAdapter(handler));
        }

        private void sendObjectImpl(final Object o, final WebSocketCallback callback) {
            try {
                if (encoding.canEncodeText(o.getClass())) {
                    WebSockets.sendText(encoding.encodeText(o), webSocketChannel, callback, sendTimeout);
                } else if (encoding.canEncodeBinary(o.getClass())) {
                    WebSockets.sendBinary(encoding.encodeBinary(o), webSocketChannel, callback, sendTimeout);
                } else {
                    // TODO: Replace on bug is fixed
                    // https://issues.jboss.org/browse/LOGTOOL-64
                    throw new EncodeException(o, "No suitable encoder found");
                }
            } catch (Exception e) {
                callback.onError(webSocketChannel, null, e);
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
            if(applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            WebSockets.sendPing(applicationData, webSocketChannel, null, sendTimeout);
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            if(applicationData == null) {
                throw JsrWebSocketMessages.MESSAGES.messageInNull();
            }
            WebSockets.sendPong(applicationData, webSocketChannel, null, sendTimeout);
        }
    }


    class BasicWebSocketSessionRemoteEndpoint implements Basic {

        private StreamSinkFrameChannel binaryFrameSender;
        private StreamSinkFrameChannel textFrameSender;

        public void assertNotInFragment() {
            if (textFrameSender != null || binaryFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
        }

        @Override
        public void sendText(final String text) throws IOException {
            assertNotInFragment();
            WebSockets.sendTextBlocking(text, webSocketChannel);
        }

        @Override
        public void sendBinary(final ByteBuffer data) throws IOException {
            assertNotInFragment();
            WebSockets.sendBinaryBlocking(data, webSocketChannel);
            data.clear(); //for some reason the TCK expects this, might as well just match the RI behaviour
        }

        @Override
        public void sendText(final String partialMessage, final boolean isLast) throws IOException {
            if (binaryFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            if (textFrameSender == null) {
                textFrameSender = webSocketChannel.send(WebSocketFrameType.TEXT);
            }
            try {
                Channels.writeBlocking(textFrameSender, WebSocketUtils.fromUtf8String(partialMessage));
                if(isLast) {
                    textFrameSender.shutdownWrites();
                }
                Channels.flushBlocking(textFrameSender);
            } finally {
                if (isLast) {
                    textFrameSender = null;
                }
            }

        }

        @Override
        public void sendBinary(final ByteBuffer partialByte, final boolean isLast) throws IOException {
            if (textFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            if (binaryFrameSender == null) {
                binaryFrameSender = webSocketChannel.send(WebSocketFrameType.BINARY);
            }
            try {
                Channels.writeBlocking(binaryFrameSender, partialByte);
                if(isLast) {
                    binaryFrameSender.shutdownWrites();
                }
                Channels.flushBlocking(binaryFrameSender);
            } finally {
                if (isLast) {
                    binaryFrameSender = null;
                }
            }
            partialByte.clear();
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            assertNotInFragment();
            //TODO: track fragment state
            return new BinaryOutputStream(webSocketChannel.send(WebSocketFrameType.BINARY));
        }

        @Override
        public Writer getSendWriter() throws IOException {
            assertNotInFragment();
            return new OutputStreamWriter(new BinaryOutputStream(webSocketChannel.send(WebSocketFrameType.TEXT)), UTF_8);
        }

        @Override
        public void sendObject(final Object data) throws IOException, EncodeException {
            sendObjectImpl(data);
        }

        private void sendObjectImpl(final Object o) throws IOException {
            try {
                if (encoding.canEncodeText(o.getClass())) {
                    WebSockets.sendTextBlocking(encoding.encodeText(o), webSocketChannel);
                } else if (encoding.canEncodeBinary(o.getClass())) {
                    WebSockets.sendBinaryBlocking(encoding.encodeBinary(o), webSocketChannel);
                } else {
                    // TODO: Replace on bug is fixed
                    // https://issues.jboss.org/browse/LOGTOOL-64
                    throw new EncodeException(o, "No suitable encoder found");
                }
            } catch (EncodeException e) {
                throw new RuntimeException(e);
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
            WebSockets.sendPingBlocking(applicationData, webSocketChannel);
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            WebSockets.sendPongBlocking(applicationData, webSocketChannel);
        }
    }
}
