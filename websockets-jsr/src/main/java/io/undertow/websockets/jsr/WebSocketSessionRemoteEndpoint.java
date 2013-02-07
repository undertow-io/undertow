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

import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.impl.WebSocketChannelSession;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfiguration;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * {@link RemoteEndpoint} implementation which uses a WebSocketSession for all its operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class WebSocketSessionRemoteEndpoint implements RemoteEndpoint {
    private final WebSocketChannelSession session;
    private volatile boolean batchingAllowed;
    private FragmentedBinaryFrameSender binaryFrameSender;
    private FragmentedTextFrameSender textFrameSender;
    private final EndpointConfiguration config;

    public WebSocketSessionRemoteEndpoint(WebSocketChannelSession session, EndpointConfiguration config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public void setBatchingAllowed(boolean batchingAllowed) {
        this.batchingAllowed = batchingAllowed;
    }

    @Override
    public boolean getBatchingAllowed() {
        return batchingAllowed;
    }

    @Override
    public void flushBatch() {
       // Do nothing
    }

    @Override
    public long getAsyncSendTimeout() {
        return session.getAsyncSendTimeout();
    }

    @Override
    public void setAsyncSendTimeout(long l) {
        session.setAsyncSendTimeout((int) l);
    }

    @Override
    public void sendString(String s) throws IOException {
        session.sendText(s);
    }

    @Override
    public void sendBytes(ByteBuffer byteBuffer) throws IOException {
        session.sendBinary(byteBuffer);
    }

    @Override
    public void sendPartialString(String text, boolean last) throws IOException {
        FragmentedTextFrameSender textFrameSender = this.textFrameSender;
        if (textFrameSender == null) {
            textFrameSender = this.textFrameSender = session.sendFragmentedText();
        }
        if (last) {
            textFrameSender.finalFragment();
            this.textFrameSender = null;
        }
        textFrameSender.sendText(text);
    }

    @Override
    public void sendPartialBytes(ByteBuffer byteBuffer, boolean last) throws IOException {
        FragmentedBinaryFrameSender binaryFrameSender = this.binaryFrameSender;
        if (binaryFrameSender == null) {
            binaryFrameSender = this.binaryFrameSender = session.sendFragmentedBinary();
        }
        if (last) {
            binaryFrameSender.finalFragment();
            this.binaryFrameSender = null;
        }
        binaryFrameSender.sendBinary(byteBuffer);
    }

    @Override
    public OutputStream getSendStream() {
        return new BinaryOutputStream(session.sendFragmentedBinary(), session.getBufferPool());
    }

    @Override
    public Writer getSendWriter() {
        return new TextWriter(session.sendFragmentedText(), session.getBufferPool());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        for (Encoder encoder : config.getEncoders()) {
            Class<?> type = ClassUtils.getEncoderType(encoder.getClass());
            if (type.isInstance(o)) {
                if (encoder instanceof Encoder.Binary) {
                    sendBytes(((Encoder.Binary) encoder).encode(o));
                    return;
                }
                if (encoder instanceof Encoder.BinaryStream) {
                    ((Encoder.BinaryStream) encoder).encode(o, getSendStream());
                    return;
                }
                if (encoder instanceof Encoder.Text) {
                    sendString(((Encoder.Text) encoder).encode(o));
                    return;
                }
                if (encoder instanceof Encoder.TextStream) {
                    ((Encoder.TextStream) encoder).encode(o, getSendWriter());
                    return;
                }
            }
        }
        // TODO: Replace on bug is fixed
        // https://issues.jboss.org/browse/LOGTOOL-64
        throw new EncodeException(o, "No suitable encoder found");
    }

    @Override
    public void sendStringByCompletion(String s, SendHandler sendHandler) {
        session.sendText(s, new SendHandlerAdapter(sendHandler));
    }

    @Override
    public Future<SendResult> sendStringByFuture(String text) {
        SendResultFuture future = new SendResultFuture();
        session.sendText(text, new SendHandlerAdapter(future));
        return future;
    }

    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer byteBuffer) {
        SendResultFuture future = new SendResultFuture();
        session.sendBinary(byteBuffer, new SendHandlerAdapter(future));
        return future;
    }

    @Override
    public void sendBytesByCompletion(ByteBuffer byteBuffer, SendHandler sendHandler) {
        session.sendBinary(byteBuffer, new SendHandlerAdapter(sendHandler));
    }

    @Override
    public Future<SendResult> sendObjectByFuture(Object o) {
        SendResultFuture future = new SendResultFuture();
        sendObjectByCompletion(o, future);
        return future;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void sendObjectByCompletion(Object o, SendHandler sendHandler) {
        try {
            for (Encoder encoder: config.getEncoders()) {
                Class<?> type = ClassUtils.getEncoderType(encoder.getClass());
                if (type.isInstance(o)) {
                    if (encoder instanceof Encoder.Binary) {
                        sendBytesByCompletion(((Encoder.Binary) encoder).encode(o), sendHandler);
                        return;
                    }
                    if (encoder instanceof Encoder.BinaryStream) {
                        ((Encoder.BinaryStream)encoder).encode(o, getSendStream());
                        sendHandler.setResult(new SendResult());
                        return;
                    }
                    if (encoder instanceof Encoder.Text) {
                        sendStringByCompletion(((Encoder.Text) encoder).encode(o), sendHandler);
                        return;
                    }
                    if (encoder instanceof Encoder.TextStream) {
                        ((Encoder.TextStream)encoder).encode(o, getSendWriter());
                        sendHandler.setResult(new SendResult());
                        return;
                    }
                }
            }
            // TODO: Replace on bug is fixed
            // https://issues.jboss.org/browse/LOGTOOL-64
            throw new EncodeException(o, "No suitable encoder found");
        } catch (Throwable e) {
            sendHandler.setResult(new SendResult(e));
        }
    }

    @Override
    public void sendPing(ByteBuffer byteBuffer) throws IOException {
        session.sendPing(byteBuffer);
    }

    @Override
    public void sendPong(ByteBuffer byteBuffer) throws IOException {
        session.sendPong(byteBuffer);
    }
}
