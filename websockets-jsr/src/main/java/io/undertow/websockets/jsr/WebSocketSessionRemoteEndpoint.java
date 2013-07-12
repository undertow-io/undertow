/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.websocket.EncodeException;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;

import io.undertow.websockets.core.BinaryOutputStream;
import io.undertow.websockets.core.FragmentedMessageChannel;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

/**
 * {@link RemoteEndpoint} implementation which uses a WebSocketSession for all its operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class WebSocketSessionRemoteEndpoint implements RemoteEndpoint {
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

        @Override
        public long getSendTimeout() {
            return 0;
            //return webSocketChannel.getAsyncSendTimeout();
        }

        @Override
        public void setSendTimeout(final long timeoutmillis) {
            //webSocketChannel.setAsyncSendTimeout((int) timeoutmillis);
        }

        @Override
        public void sendText(final String text, final SendHandler handler) {
            WebSockets.sendText(text, webSocketChannel, new SendHandlerAdapter(handler));
        }

        @Override
        public Future<Void> sendText(final String text) {
            final SendResultFuture future = new SendResultFuture();
            WebSockets.sendText(text, webSocketChannel, future);
            return future;
        }

        @Override
        public Future<Void> sendBinary(final ByteBuffer data) {
            final SendResultFuture future = new SendResultFuture();
            WebSockets.sendBinary(data, webSocketChannel, future);
            return future;
        }

        @Override
        public void sendBinary(final ByteBuffer data, final SendHandler completion) {
            WebSockets.sendBinary(data, webSocketChannel, new SendHandlerAdapter(completion));
        }

        @Override
        public Future<Void> sendObject(final Object o) {
            final SendResultFuture future = new SendResultFuture();
            sendObjectImpl(o, future);
            return future;
        }

        @Override
        public void sendObject(final Object data, final SendHandler handler) {
            sendObjectImpl(data, new SendHandlerAdapter(handler));
        }

        private void sendObjectImpl(final Object o, final WebSocketCallback callback) {
            try {
                if (encoding.canEncodeText(o.getClass())) {
                    WebSockets.sendText(encoding.encodeText(o), webSocketChannel, callback);
                } else if (encoding.canEncodeBinary(o.getClass())) {
                    WebSockets.sendBinary(encoding.encodeBinary(o), webSocketChannel, callback);
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
            WebSockets.sendPing(applicationData, webSocketChannel, null);
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            WebSockets.sendPong(applicationData, webSocketChannel, null);
        }
    }


    class BasicWebSocketSessionRemoteEndpoint implements Basic {

        private FragmentedMessageChannel binaryFrameSender;
        private FragmentedMessageChannel textFrameSender;

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
        }

        @Override
        public void sendText(final String partialMessage, final boolean isLast) throws IOException {
            if (binaryFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            if (textFrameSender == null) {
                textFrameSender = webSocketChannel.sendFragmentedText();
            }
            try {
                WebSockets.sendTextBlocking(partialMessage, isLast, textFrameSender);
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
                binaryFrameSender = webSocketChannel.sendFragmentedBinary();
            }
            try {
                WebSockets.sendBinaryBlocking(partialByte, isLast, textFrameSender);
            } finally {
                if (isLast) {
                    binaryFrameSender = null;
                }
            }
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            assertNotInFragment();
            //TODO: track fragment state
            return new BinaryOutputStream(webSocketChannel.sendFragmentedBinary(), webSocketChannel.getBufferPool());
        }

        @Override
        public Writer getSendWriter() throws IOException {
            assertNotInFragment();
            return new OutputStreamWriter(new BinaryOutputStream(webSocketChannel.sendFragmentedText(), webSocketChannel.getBufferPool()));
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
            WebSockets.sendPingBlocking(applicationData, webSocketChannel);
        }
    }
}
