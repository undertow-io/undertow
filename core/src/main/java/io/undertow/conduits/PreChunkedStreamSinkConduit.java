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

package io.undertow.conduits;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.util.Attachable;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Channel that implements HTTP chunked transfer coding for data streams that already have chunk markers.
 *
 * @author Stuart Douglas
 */
public class PreChunkedStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final ConduitListener<? super PreChunkedStreamSinkConduit> finishListener;

    /**
     * Flag that is set when {@link #terminateWrites()} or @{link #close()} is called
     */
    private static final int FLAG_WRITES_SHUTDOWN = 1;
    private static final int FLAG_FINISHED = 1 << 2;

    int state = 0;
    final ChunkReader<PreChunkedStreamSinkConduit> chunkReader;

    /**
     * Construct a new instance.
     *
     * @param next           the channel to wrap
     * @param finishListener The finish listener
     * @param attachable     The attachable
     */
    public PreChunkedStreamSinkConduit(final StreamSinkConduit next, final ConduitListener<? super PreChunkedStreamSinkConduit> finishListener, final Attachable attachable) {
        super(next);
        //we don't want the reader to call the finish listener, so we pass null
        this.chunkReader = new ChunkReader<>(attachable, HttpAttachments.RESPONSE_TRAILERS, this);
        this.finishListener = finishListener;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return doWrite(src);
    }


    int doWrite(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (chunkReader.getChunkRemaining() == -1) {
            throw UndertowMessages.MESSAGES.extraDataWrittenAfterChunkEnd();
        }
        if (src.remaining() == 0) {
            return 0;
        }
        int oldPos = src.position();
        int oldLimit = src.limit();
        int ret = next.write(src);
        if(ret == 0) {
            return ret;
        }
        int newPos = src.position();
        src.position(oldPos);
        src.limit(oldPos + ret);
        try {
            while (true) {
                long chunkRemaining = chunkReader.readChunk(src);
                if (chunkRemaining == -1) {
                    if (src.remaining() == 0) {
                        return ret;
                    } else {
                        throw UndertowMessages.MESSAGES.extraDataWrittenAfterChunkEnd();
                    }
                } else if(chunkRemaining == 0) {
                    return ret;
                }
                int remaining;
                if (src.remaining() >= chunkRemaining) {
                    src.position((int) (src.position() + chunkRemaining));
                    remaining = 0;
                } else {
                    remaining = (int) (chunkRemaining - src.remaining());
                    src.position(src.limit());
                }
                chunkReader.setChunkRemaining(remaining);
                if (!src.hasRemaining()) {
                    break;
                }
            }
        } finally {
            src.position(newPos);
            src.limit(oldLimit);
        }
        return ret;
    }


    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        for (int i = offset; i < length; ++i) {
            if (srcs[i].hasRemaining()) {
                return write(srcs[i]);
            }
        }
        return 0;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            terminateWrites();
            return 0;
        }
        int ret = doWrite(src);
        terminateWrites();
        return ret;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public boolean flush() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            boolean val = next.flush();
            if (val && allAreClear(state, FLAG_FINISHED)) {
                invokeFinishListener();
            }
            return val;
        } else {
            return next.flush();
        }
    }

    private void invokeFinishListener() {
        state |= FLAG_FINISHED;
        if (finishListener != null) {
            finishListener.handleEvent(this);
        }
    }

    @Override
    public void terminateWrites() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            return;
        }
        if (chunkReader.getChunkRemaining() != -1) {
            throw UndertowMessages.MESSAGES.chunkedChannelClosedMidChunk();
        }
        state |= FLAG_WRITES_SHUTDOWN;
    }

    @Override
    public void awaitWritable() throws IOException {
        next.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        next.awaitWritable(time, timeUnit);
    }
}
