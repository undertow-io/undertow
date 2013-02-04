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
import io.undertow.websockets.api.FragmentedSender;
import io.undertow.websockets.api.PingFrameSender;
import io.undertow.websockets.api.PongFrameSender;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.api.BinaryFrameSender;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.api.TextFrameSender;
import io.undertow.websockets.api.WebSocketSession;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link WebSocketSession} implementation which wraps a {@link WebSocketChannel} and operate on its API.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketChannelSession implements WebSocketSession {
    private final WebSocketChannel channel;
    private final String id;
    // TODO: Maybe init lazy to safe memory when not used by the user ?
    private final ConcurrentMap<String, Object> attrs = new ConcurrentHashMap<String, Object>();
    @SuppressWarnings("unused")
    private volatile FrameHandler frameHandler;
    private static final AtomicReferenceFieldUpdater<WebSocketChannelSession, FrameHandler> FRAME_HANDLER_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(WebSocketChannelSession.class, FrameHandler.class, "frameHandler");

    private FragmentedSender activeSender;

    private final TextFrameSender textFrameSender;
    private final BinaryFrameSender binaryFrameSender;
    private final PingFrameSender pingFrameSender;
    private final PongFrameSender pongFrameSender;
    private final CloseFrameSender closeFrameSender;
    private volatile int asyncSendTimeout;

    public WebSocketChannelSession(WebSocketChannel channel, String id) {
        this.channel = channel;
        this.id = id;
        textFrameSender = new DefaultTextFrameSender(this);
        binaryFrameSender = new DefaultBinaryFrameSender(this);
        pingFrameSender = new DefaultPingFrameSender(this);
        pongFrameSender = new DefaultPongFrameSender(this);
        closeFrameSender = new DefaultCloseFrameSender(this);
    }

    @Override
    public void setIdleTimeout(int idleTimeout) {
        try {
            channel.setOption(UndertowOptions.IDLE_TIMEOUT, idleTimeout);
        } catch (IOException e) {
            // log this
            WebSocketLogger.REQUEST_LOGGER.setIdleTimeFailed(e);
        }
    }

    @Override
    public int getIdleTimeout() {
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
        if (value == null) {
            return attrs.remove(key) != null;
        } else {
            return attrs.putIfAbsent(key, value) == null;
        }
    }

    @Override
    public Object getAttribute(String key) {
        return attrs.get(key);
    }

    @Override
    public boolean isSecure() {
        return channel.isSecure();
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public FrameHandler setFrameHandler(FrameHandler handler) {
        return FRAME_HANDLER_UPDATER.getAndSet(this, handler);
    }

    @Override
    public FrameHandler getFrameHandler() {
        return frameHandler;
    }

    private synchronized <T extends FragmentedSender> T checkFragmentedSender(T sender) {
        if (activeSender == null) {
            activeSender = sender;
            return sender;
        }
         if (activeSender == sender) {
            return sender;
        }
        throw WebSocketMessages.MESSAGES.fragmentedSenderInUse();
    }

    private synchronized void checkSender() {
        if (activeSender != null) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void sendPing(ByteBuffer payload, SendCallback callback) {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pingFrameSender.sendPing(payload,callback);
    }

    @Override
    public void sendPing(ByteBuffer[] payload, SendCallback callback) {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pingFrameSender.sendPing(payload,callback);
    }

    @Override
    public void sendPing(ByteBuffer payload) throws IOException {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pingFrameSender.sendPing(payload);
    }

    @Override
    public void sendPing(ByteBuffer[] payload) throws IOException {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pingFrameSender.sendPing(payload);
    }

    @Override
    public void sendPong(ByteBuffer payload, SendCallback callback) {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pongFrameSender.sendPong(payload, callback);
    }

    @Override
    public void sendPong(ByteBuffer[] payload, SendCallback callback) {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pongFrameSender.sendPong(payload, callback);
    }

    @Override
    public void sendPong(ByteBuffer payload) throws IOException {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pongFrameSender.sendPong(payload);
    }

    @Override
    public void sendPong(ByteBuffer[] payload) throws IOException {
        // no need to check sender as ping and pong frames are allowed between as stated in the rfc
        pongFrameSender.sendPong(payload);
    }

    @Override
    public FragmentedBinaryFrameSender sendFragmentedBinary() {
        return checkFragmentedSender(new DefaultFragmentedBinaryFrameSender(this));
    }

    @Override
    public FragmentedTextFrameSender sendFragmentedText() {
        return checkFragmentedSender(new DefaultFragmentedTextFrameSender(this));
    }

    @Override
    public void sendBinary(final ByteBuffer[] payload, final SendCallback callback) {
       checkSender();
       binaryFrameSender.sendBinary(payload, callback);
    }

    @Override
    public void sendBinary(ByteBuffer payload) throws IOException {
        checkSender();
        binaryFrameSender.sendBinary(payload);
    }

    @Override
    public void sendBinary(ByteBuffer[] payload) throws IOException {
        checkSender();
        binaryFrameSender.sendBinary(payload);
    }

    @Override
    public void sendBinary(ByteBuffer payload, SendCallback callback) {
        checkSender();
        binaryFrameSender.sendBinary(payload, callback);
    }

    @Override
    public void sendBinary(FileChannel payloadChannel, int offset, long length, SendCallback callback) {
        checkSender();
        binaryFrameSender.sendBinary(payloadChannel, offset, length, callback);
    }

    @Override
    public OutputStream sendBinary(long payloadSize) throws IOException {
        checkSender();
        return binaryFrameSender.sendBinary(payloadSize);
    }

    @Override
    public void sendText(CharSequence payload, SendCallback callback) {
        checkSender();
        textFrameSender.sendText(payload, callback);
    }

    @Override
    public void sendText(CharSequence payload) throws IOException {
        checkSender();
        textFrameSender.sendText(payload);
    }

    @Override
    public Writer sendText(long payloadSize) throws IOException {
        checkSender();
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

    synchronized void complete(FragmentedSender sender) {
        if (activeSender != sender) {
            throw WebSocketMessages.MESSAGES.fragmentedSenderInUse();
        }
        activeSender = null;
    }

    WebSocketChannel getChannel() {
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
}
