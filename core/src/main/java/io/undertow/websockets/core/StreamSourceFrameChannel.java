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

package io.undertow.websockets.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import io.undertow.websockets.core.function.ChannelFunction;
import io.undertow.websockets.core.function.ChannelFunctionFileChannel;
import io.undertow.websockets.core.protocol.version07.Masker;
import io.undertow.websockets.core.protocol.version07.UTF8Checker;
import io.undertow.websockets.extensions.ExtensionByteBuffer;
import io.undertow.websockets.extensions.ExtensionFunction;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;

/**
 * Base class for processes Frame bases StreamSourceChannels.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSourceFrameChannel extends AbstractFramedStreamSourceChannel<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    protected final WebSocketFrameType type;

    private boolean finalFragment;
    private final int rsv;
    private final ChannelFunction[] functions;
    private final List<ExtensionFunction> extensions;
    private ExtensionByteBuffer extensionResult;
    private Masker masker;
    private UTF8Checker checker;

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, Pooled<ByteBuffer> pooled, long frameLength) {
        this(wsChannel, type, 0, true, pooled, frameLength);
    }

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, int rsv, boolean finalFragment, Pooled<ByteBuffer> pooled, long frameLength, ChannelFunction... functions) {
        super(wsChannel, pooled, frameLength);
        this.type = type;
        this.finalFragment = finalFragment;
        this.rsv = rsv;

        this.functions = functions;
        masker = null;
        checker = null;
        for (ChannelFunction func : functions) {
            if (func instanceof Masker) {
                masker = (Masker) func;
            }
            if (func instanceof UTF8Checker) {
                checker = (UTF8Checker) func;
            }
        }
        if (wsChannel.areExtensionsSupported() && wsChannel.getExtensions() != null && !wsChannel.getExtensions().isEmpty()) {
            extensions = wsChannel.getExtensions();
        } else {
            extensions = null;
        }
        this.extensionResult = null;
    }



    /**
     * Return the {@link WebSocketFrameType} or {@code null} if its not known at the calling time.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Return the rsv which is used for extensions.
     */
    public int getRsv() {
        return rsv;
    }

    int getWebSocketFrameCount() {
        return getReadFrameCount();
    }

    @Override
    protected WebSocketChannel getFramedChannel() {
        return (WebSocketChannel) super.getFramedChannel();
    }

    public WebSocketChannel getWebSocketChannel() {
        return getFramedChannel();
    }

    public void finalFrame() {
        this.lastFrame();
        this.finalFragment = true;
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        super.handleHeaderData(headerData);
        if (((WebSocketFrame) headerData).isFinalFragment()) {
            finalFrame();
        }
        if(functions != null) {
            for(ChannelFunction func : functions) {
                func.newFrame(headerData);
            }
        }
    }


    @Override
    public final long transferTo(long position, long count, FileChannel target) throws IOException {
        long r;
        if (functions != null && functions.length > 0) {
            r = super.transferTo(position, count, new ChannelFunctionFileChannel(target, functions));
        } else {
            r = super.transferTo(position, count, target);
        }
        return r;
    }

    @Override
    public final long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        // use this because of XNIO bug
        // See https://issues.jboss.org/browse/XNIO-185
        return WebSocketUtils.transfer(this, count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int r;
        int position = dst.position();
        if (extensionResult == null) {
            r = super.read(dst);
            if (r > 0) {
                masker(dst, position, r);
            }
            if (getRsv() > 0) {
                extensionResult = applyExtensions(dst, position, r);
            }
            if (r > 0) {
                boolean complete = isComplete() && extensionResult == null;
                checker(dst, position, dst.position() - position, complete);
            }
            return r;
        } else {
            r = extensionResult.flushExtra(dst);
            if (!extensionResult.hasExtra()) {
                extensionResult.free();
                extensionResult = null;
            }
            if (r > 0) {
                checker(dst, position, dst.position() - position, isComplete() && extensionResult == null);
            }
            return r;
        }
    }

    @Override
    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        Bounds[] old = new Bounds[length];
        for (int i = offset; i < length; i++) {
            ByteBuffer dst = dsts[i];
            old[i - offset] = new Bounds(dst.position(), dst.limit());
        }
        long b = super.read(dsts, offset, length);
        if (b > 0) {
            for (int i = offset; i < length; i++) {
                ByteBuffer dst = dsts[i];
                int oldPos = old[i - offset].position;
                afterRead(dst, oldPos, dst.position() - oldPos);
            }
        }
        return b;
    }

    /**
     * Called after data was read into the {@link ByteBuffer}
     *
     * @param buffer   the {@link ByteBuffer} into which the data was read
     * @param position the position it was written to
     * @param length   the number of bytes there were written
     * @throws IOException thrown if an error occurs
     */
    protected void afterRead(ByteBuffer buffer, int position, int length) throws IOException {
        try {
            for (ChannelFunction func : functions) {
                func.afterRead(buffer, position, length);
            }
            if (isComplete()) {
                try {
                    for (ChannelFunction func : functions) {
                        func.complete();
                    }
                } catch (UnsupportedEncodingException e) {
                    getFramedChannel().markReadsBroken(e);
                    throw e;
                }
            }
        } catch (UnsupportedEncodingException e) {
            getFramedChannel().markReadsBroken(e);
            throw e;
        }
    }

    protected void masker(ByteBuffer buffer, int position, int length) throws IOException {
        if (masker == null) {
            return;
        }
        masker.afterRead(buffer, position, length);
    }

    protected void checker(ByteBuffer buffer, int position, int length, boolean complete) throws IOException {
        if (checker == null) {
            return;
        }
        try {
            checker.afterRead(buffer, position, length);
            if (complete) {
                try {
                    checker.complete();
                } catch (UnsupportedEncodingException e) {
                    getFramedChannel().markReadsBroken(e);
                    throw e;
                }
            }
        } catch (UnsupportedEncodingException e) {
            getFramedChannel().markReadsBroken(e);
            throw e;
        }
    }

    /**
     * Process Extensions chain after a read operation.
     * <p>
     * An extension can modify original content beyond {@code ByteBuffer} capacity,then original buffer is wrapped with
     * {@link ExtensionByteBuffer} class. {@code ExtensionByteBuffer} stores extra buffer to manage overflow of original
     * {@code ByteBuffer} .
     *
     * @param buffer    the buffer to operate on
     * @param position  the index in the buffer to start from
     * @param length    the number of bytes to operate on
     * @return          a {@link ExtensionByteBuffer} instance as a wrapper of original buffer with extra buffers;
     *                  {@code null} if no extra buffers needed
     * @throws IOException
     */
    protected ExtensionByteBuffer applyExtensions(final ByteBuffer buffer, final int position, final int length) throws IOException {
        ExtensionByteBuffer extBuffer = new ExtensionByteBuffer(getWebSocketChannel(), buffer, position);
        int newLength = length;
        if (extensions != null) {
            for (ExtensionFunction ext : extensions) {
                ext.afterRead(this, extBuffer, position, newLength);
                if (extBuffer.getFilled() == 0) {
                    buffer.position(position);
                    newLength = 0;
                } else if (extBuffer.getFilled() != newLength) {
                    newLength = extBuffer.getFilled();
                }
            }
        }
        if (!extBuffer.hasExtra()) {
            return null;
        }
        return extBuffer;
    }

    private static class Bounds {
        final int position;
        final int limit;

        Bounds(int position, int limit) {
            this.position = position;
            this.limit = limit;
        }
    }
}
