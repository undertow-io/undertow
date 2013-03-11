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

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfiguration;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;

import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.impl.WebSocketChannelSession;
import io.undertow.websockets.jsr.util.ClassUtils;

/**
 * {@link RemoteEndpoint} implementation which uses a WebSocketSession for all its operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class WebSocketSessionRemoteEndpoint implements RemoteEndpoint {
    private final WebSocketChannelSession session;
    private final EndpointConfiguration config;
    private final Async async = new AsyncWebSocketSessionRemoteEndpoint();
    private final Basic basic = new BasicWebSocketSessionRemoteEndpoint();

    public WebSocketSessionRemoteEndpoint(WebSocketChannelSession session, EndpointConfiguration config) {
        this.session = session;
        this.config = config;
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
        session.sendPing(applicationData);
    }

    @Override
    public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        session.sendPong(applicationData);
    }

    class AsyncWebSocketSessionRemoteEndpoint implements Async {

        @Override
        public long getSendTimeout() {
            return session.getAsyncSendTimeout();
        }

        @Override
        public void setSendTimeout(final long timeoutmillis) {
            session.setAsyncSendTimeout((int) timeoutmillis);
        }

        @Override
        public void sendText(final String text, final SendHandler handler) {
            session.sendText(text, new SendHandlerAdapter(handler));
        }

        @Override
        public Future<Void> sendText(final String text) {
            final SendResultFuture future = new SendResultFuture();
            session.sendText(text, future);
            return future;
        }

        @Override
        public Future<Void> sendBinary(final ByteBuffer data) {
            final SendResultFuture future = new SendResultFuture();
            session.sendBinary(data, future);
            return future;
        }

        @Override
        public void sendBinary(final ByteBuffer data, final SendHandler completion) {
            session.sendBinary(data, new SendHandlerAdapter(completion));
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

        private void sendObjectImpl(final Object o, final SendCallback callback) {
            try {
                for (Encoder encoder : config.getEncoders()) {
                    Class<?> type = ClassUtils.getEncoderType(encoder.getClass());
                    if (type.isInstance(o)) {
                        if (encoder instanceof Encoder.Binary) {
                            session.sendBinary(((Encoder.Binary) encoder).encode(o), callback);
                            return;
                        }
                        if (encoder instanceof Encoder.BinaryStream) {
                            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            ((Encoder.BinaryStream) encoder).encode(o, stream);
                            session.sendBinary(ByteBuffer.wrap(stream.toByteArray()), callback);
                            return;
                        }
                        if (encoder instanceof Encoder.Text) {
                            session.sendText(((Encoder.Text) encoder).encode(o), callback);
                            return;
                        }
                        if (encoder instanceof Encoder.TextStream) {
                            final CharArrayWriter writer = new CharArrayWriter();
                            ((Encoder.TextStream) encoder).encode(o, writer);
                            session.sendText(new String(writer.toCharArray()), callback);
                            return;
                        }
                    }
                }
                // TODO: Replace on bug is fixed
                // https://issues.jboss.org/browse/LOGTOOL-64
                throw new EncodeException(o, "No suitable encoder found");
            } catch (IOException | EncodeException e) {
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
            session.sendPing(applicationData);
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            session.sendPong(applicationData);
        }
    }


    class BasicWebSocketSessionRemoteEndpoint implements Basic {

        private FragmentedBinaryFrameSender binaryFrameSender;
        private FragmentedTextFrameSender textFrameSender;

        public void assertNotInFragment() {
            if (textFrameSender != null || binaryFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
        }

        @Override
        public void sendText(final String text) throws IOException {
            assertNotInFragment();
            session.sendText(text);
        }

        @Override
        public void sendBinary(final ByteBuffer data) throws IOException {
            assertNotInFragment();
            session.sendBinary(data);
        }

        @Override
        public void sendText(final String partialMessage, final boolean isLast) throws IOException {
            if (binaryFrameSender != null) {
                throw JsrWebSocketMessages.MESSAGES.cannotSendInMiddleOfFragmentedMessage();
            }
            if (textFrameSender == null) {
                textFrameSender = session.sendFragmentedText();
            }
            if (isLast) {
                textFrameSender.finalFragment();
            }
            try {
                textFrameSender.sendText(partialMessage);
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
                binaryFrameSender = session.sendFragmentedBinary();
            }
            if (isLast) {
                binaryFrameSender.finalFragment();
            }
            try {
                binaryFrameSender.sendBinary(partialByte);
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
            return new BinaryOutputStream(session.sendFragmentedBinary(), session.getBufferPool());
        }

        @Override
        public Writer getSendWriter() throws IOException {
            assertNotInFragment();
            return new TextWriter(session.sendFragmentedText(), session.getBufferPool());
        }

        @Override
        public void sendObject(final Object data) throws IOException, EncodeException {
            sendObjectImpl(data);
        }

        private void sendObjectImpl(final Object o) throws IOException {
            try {
                for (Encoder encoder : config.getEncoders()) {
                    Class<?> type = ClassUtils.getEncoderType(encoder.getClass());
                    if (type.isInstance(o)) {
                        if (encoder instanceof Encoder.Binary) {
                            session.sendBinary(((Encoder.Binary) encoder).encode(o));
                            return;
                        }
                        if (encoder instanceof Encoder.BinaryStream) {
                            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            ((Encoder.BinaryStream) encoder).encode(o, stream);
                            session.sendBinary(ByteBuffer.wrap(stream.toByteArray()));
                            return;
                        }
                        if (encoder instanceof Encoder.Text) {
                            session.sendText(((Encoder.Text) encoder).encode(o));
                            return;
                        }
                        if (encoder instanceof Encoder.TextStream) {
                            final CharArrayWriter writer = new CharArrayWriter();
                            ((Encoder.TextStream) encoder).encode(o, writer);
                            session.sendText(new String(writer.toCharArray()));
                            return;
                        }
                    }
                }
                // TODO: Replace on bug is fixed
                // https://issues.jboss.org/browse/LOGTOOL-64
                throw new EncodeException(o, "No suitable encoder found");
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
            session.sendPing(applicationData);
        }

        @Override
        public void sendPong(final ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            session.sendPong(applicationData);
        }
    }

}
