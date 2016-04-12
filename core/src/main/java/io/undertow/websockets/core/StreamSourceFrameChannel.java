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

import io.undertow.connector.PooledByteBuffer;
import io.undertow.websockets.core.function.ChannelFunction;
import io.undertow.websockets.core.function.ChannelFunctionFileChannel;
import io.undertow.websockets.core.protocol.version07.Masker;
import io.undertow.websockets.core.protocol.version07.UTF8Checker;
import io.undertow.websockets.extensions.ExtensionFunction;
import io.undertow.websockets.extensions.NoopExtensionFunction;
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
    private final ExtensionFunction extensionFunction;
    private Masker masker;
    private UTF8Checker checker;

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, PooledByteBuffer pooled, long frameLength) {
        this(wsChannel, type, 0, true, pooled, frameLength, null);
    }

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, int rsv, boolean finalFragment, PooledByteBuffer pooled, long frameLength, Masker masker, ChannelFunction... functions) {
        super(wsChannel, pooled, frameLength);
        this.type = type;
        this.finalFragment = finalFragment;
        this.rsv = rsv;

        this.functions = functions;
        this.masker = masker;
        checker = null;
        for (ChannelFunction func : functions) {
            if (func instanceof UTF8Checker) {
                checker = (UTF8Checker) func;
            }
        }
        if (rsv > 0) {
            this.extensionFunction = wsChannel.getExtensionFunction();
        } else {
            this.extensionFunction = NoopExtensionFunction.INSTANCE;
        }
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
        return super.getFramedChannel();
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
        if(masker != null) {
            masker.newFrame(headerData);
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
        int position = dst.position();
        int r = super.read(dst);
        if (r > 0) {
            checker(dst, position, dst.position() - position, false);
        } else if(r == -1) {
            checkComplete();
        }
        return r;
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
        } else if(b == -1){
            checkComplete();
        }
        return b;
    }

    private void checkComplete() throws IOException {
        try {
            for (ChannelFunction func : functions) {
                func.complete();
            }
        } catch (UnsupportedEncodingException e) {
            getFramedChannel().markReadsBroken(e);
            throw e;
        }
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
                checkComplete();
            }
        } catch (UnsupportedEncodingException e) {
            getFramedChannel().markReadsBroken(e);
            throw e;
        }
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

    @Override
    protected PooledByteBuffer processFrameData(PooledByteBuffer frameData, boolean lastFragmentOfFrame) throws IOException {
        if(masker != null) {
            masker.afterRead(frameData.getBuffer(), frameData.getBuffer().position(), frameData.getBuffer().remaining());
        }
        try {
            return extensionFunction.transformForRead(frameData, this, lastFragmentOfFrame && isFinalFragment());
        } catch (IOException e) {
            getWebSocketChannel().markReadsBroken(new WebSocketFrameCorruptedException(e));
            throw e;
        } catch (Exception e) {
            getWebSocketChannel().markReadsBroken(new WebSocketFrameCorruptedException(e));
            throw new IOException(e);
        }
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
