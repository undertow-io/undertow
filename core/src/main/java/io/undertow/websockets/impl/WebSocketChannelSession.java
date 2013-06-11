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
package io.undertow.websockets.impl;

import io.undertow.UndertowOptions;
import io.undertow.websockets.api.CloseFrameSender;
import io.undertow.websockets.api.PingFrameSender;
import io.undertow.websockets.api.PongFrameSender;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.api.BinaryFrameSender;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.api.TextFrameSender;
import io.undertow.websockets.api.WebSocketSession;
import org.xnio.Pool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link WebSocketSession} implementation which wraps a {@link WebSocketChannel} and operate on its API.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketChannelSession implements WebSocketSession {
    private final WebSocketChannel channel;
    private final String id;

    @SuppressWarnings("unused")
    private volatile FrameHandler frameHandler;
    private static final AtomicReferenceFieldUpdater<WebSocketChannelSession, FrameHandler> FRAME_HANDLER_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(WebSocketChannelSession.class, FrameHandler.class, "frameHandler");

    private final TextFrameSender textFrameSender;
    private final BinaryFrameSender binaryFrameSender;
    private final PingFrameSender pingFrameSender;
    private final PongFrameSender pongFrameSender;
    private final CloseFrameSender closeFrameSender;
    private volatile int asyncSendTimeout;
    private volatile long maxTextFrameSize;
    private volatile long maxBinaryFrameSize;
    private final Executor frameHandlerExecutor;
    boolean closeFrameSent;
    final boolean executeInIoThread;
    public WebSocketChannelSession(WebSocketChannel channel, String id, boolean executeInIoThread) {
        this.channel = channel;
        this.id = id;
        this.executeInIoThread = executeInIoThread;
        textFrameSender = new DefaultTextFrameSender(this);
        binaryFrameSender = new DefaultBinaryFrameSender(this);
        pingFrameSender = new DefaultPingFrameSender(this);
        pongFrameSender = new DefaultPongFrameSender(this);
        closeFrameSender = new DefaultCloseFrameSender(this);
        frameHandlerExecutor = new FrameHandlerExecutor(channel.getWorker());

    }

    @Override
    public void setIdleTimeout(long idleTimeout) {
        try {
            channel.setOption(UndertowOptions.IDLE_TIMEOUT, idleTimeout);
        } catch (IOException e) {
            // log this
            WebSocketLogger.REQUEST_LOGGER.setIdleTimeFailed(e);
        }
    }

    @Override
    public long getIdleTimeout() {
        try {
            return channel.getOption(UndertowOptions.IDLE_TIMEOUT);
        } catch (IOException e) {
            // log this
            WebSocketLogger.REQUEST_LOGGER.getIdleTimeFailed(e);
            return 0;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean setAttribute(String key, Object value) {
        return channel.setAttribute(key, value);
    }

    @Override
    public Object getAttribute(String key) {
        return channel.getAttribute(key);
    }

    @Override
    public boolean isSecure() {
        return channel.isSecure();
    }

    @Override
    public FrameHandler setFrameHandler(FrameHandler handler) {
        return FRAME_HANDLER_UPDATER.getAndSet(this, handler);
    }

    @Override
    public FrameHandler getFrameHandler() {
        return frameHandler;
    }

    @Override
    public void sendPing(ByteBuffer payload, SendCallback callback) {
        pingFrameSender.sendPing(payload,callback);
    }

    @Override
    public void sendPing(ByteBuffer[] payload, SendCallback callback) {
        pingFrameSender.sendPing(payload,callback);
    }

    @Override
    public void sendPing(ByteBuffer payload) throws IOException {
        pingFrameSender.sendPing(payload);
    }

    @Override
    public void sendPing(ByteBuffer[] payload) throws IOException {
        pingFrameSender.sendPing(payload);
    }

    @Override
    public void sendPong(ByteBuffer payload, SendCallback callback) {
        pongFrameSender.sendPong(payload, callback);
    }

    @Override
    public void sendPong(ByteBuffer[] payload, SendCallback callback) {
        pongFrameSender.sendPong(payload, callback);
    }

    @Override
    public void sendPong(ByteBuffer payload) throws IOException {
        pongFrameSender.sendPong(payload);
    }

    @Override
    public void sendPong(ByteBuffer[] payload) throws IOException {
        pongFrameSender.sendPong(payload);
    }

    @Override
    public FragmentedBinaryFrameSender sendFragmentedBinary() {
        return new DefaultFragmentedBinaryFrameSender(this);
    }

    @Override
    public FragmentedTextFrameSender sendFragmentedText() {
        return new DefaultFragmentedTextFrameSender(this);
    }

    @Override
    public void sendBinary(final ByteBuffer[] payload, final SendCallback callback) {
       binaryFrameSender.sendBinary(payload, callback);
    }

    @Override
    public void sendBinary(ByteBuffer payload) throws IOException {
        binaryFrameSender.sendBinary(payload);
    }

    @Override
    public void sendBinary(ByteBuffer[] payload) throws IOException {
        binaryFrameSender.sendBinary(payload);
    }

    @Override
    public void sendBinary(ByteBuffer payload, SendCallback callback) {
        binaryFrameSender.sendBinary(payload, callback);
    }

    @Override
    public void sendBinary(FileChannel payloadChannel, int offset, long length, SendCallback callback) {
        binaryFrameSender.sendBinary(payloadChannel, offset, length, callback);
    }

    @Override
    public OutputStream sendBinary(long payloadSize) throws IOException {
        return binaryFrameSender.sendBinary(payloadSize);
    }

    @Override
    public void sendText(CharSequence payload, SendCallback callback) {
        textFrameSender.sendText(payload, callback);
    }

    @Override
    public void sendText(CharSequence payload) throws IOException {
        textFrameSender.sendText(payload);
    }

    @Override
    public Writer sendText(long payloadSize) throws IOException {
        return textFrameSender.sendText(payloadSize);
    }

    @Override
    public Set<String> getSubProtocols() {
        return channel.getSubProtocols();
    }

    @Override
    public void sendClose(CloseReason reason, SendCallback callback) {
        closeFrameSender.sendClose(reason, callback);
    }

    @Override
    public void sendClose(CloseReason reason) throws IOException {
        closeFrameSender.sendClose(reason);
    }

    public WebSocketChannel getChannel() {
        return channel;
    }

    @Override
    public void setAsyncSendTimeout(int asyncSendTimeout) {
        this.asyncSendTimeout = asyncSendTimeout;
    }

    @Override
    public int getAsyncSendTimeout() {
        return asyncSendTimeout;
    }

    @Override
    public void setMaximumTextFrameSize(long size) {
        maxTextFrameSize = size;
    }

    @Override
    public long getMaximumTextFrameSize() {
        return maxTextFrameSize;
    }

    @Override
    public void setMaximumBinaryFrameSize(long size) {
        maxBinaryFrameSize = size;
    }

    @Override
    public long getMaximumBinaryFrameSize() {
        return maxBinaryFrameSize;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean isCloseFrameReceived() {
        return channel.isCloseFrameReceived();
    }

    @Override
    public String getProtocolVersion() {
        return channel.getVersion().toHttpHeaderValue();
    }

    Executor getFrameHandlerExecutor() {
        return frameHandlerExecutor;
    }

    public Pool<ByteBuffer> getBufferPool() {
        return channel.getBufferPool();
    }
}
