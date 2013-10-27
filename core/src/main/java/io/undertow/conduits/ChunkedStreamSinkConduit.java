/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowMessages;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.anyAreSet;

/**
 * Channel that implements HTTP chunked transfer coding.
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    /**
     * Trails that are to be attached to the end of the HTTP response. Note that it is the callers responsibility
     * to make sure the client understands trailers (i.e. they have provided a TE header), and to set the 'Trailers:'
     * header appropriately.
     *
     * This attachment must be set before the {@link #terminateWrites()} method is called.
     */
    public static final AttachmentKey<HeaderMap> TRAILERS = AttachmentKey.create(HeaderMap.class);

    private final HeaderMap responseHeaders;

    private final ConduitListener<? super ChunkedStreamSinkConduit> finishListener;
    private final int config;

    private static final byte[] LAST_CHUNK = "0\r\n".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();

    private final Attachable attachable;
    private int state;
    private int chunkleft = 0;

    private final ByteBuffer chunkingBuffer = ByteBuffer.allocate(14); //14 is the most
    private ByteBuffer trailerBuffer;


    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    /**
     * Flag that is set when {@link #shutdownWrites()} or @{link #close()} is called
     */
    private static final int FLAG_WRITES_SHUTDOWN = 1;
    private static final int FLAG_NEXT_SHUTDWON = 1 << 2;
    private static final int FLAG_WRITTEN_FIRST_CHUNK = 1 << 3;

    int written = 0;
    /**
     * Construct a new instance.
     *
     * @param next       the channel to wrap
     * @param configurable   {@code true} to allow configuration of the next channel, {@code false} otherwise
     * @param passClose      {@code true} to close the underlying channel when this channel is closed, {@code false} otherwise
     * @param responseHeaders The response headers
     * @param finishListener The finish listener
     * @param attachable The attachable
     */
    public ChunkedStreamSinkConduit(final StreamSinkConduit next, final boolean configurable, final boolean passClose, HeaderMap responseHeaders, final ConduitListener<? super ChunkedStreamSinkConduit> finishListener, final Attachable attachable) {
        super(next);
        this.responseHeaders = responseHeaders;
        this.finishListener = finishListener;
        this.attachable = attachable;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (passClose ? CONF_FLAG_PASS_CLOSE : 0);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (chunkleft == 0) {
            chunkingBuffer.clear();
            if(anyAreSet(state, FLAG_WRITTEN_FIRST_CHUNK)) {
                chunkingBuffer.put(CRLF);
            }
            written += src.remaining();
            chunkingBuffer.put(Integer.toHexString(src.remaining()).getBytes());
            chunkingBuffer.put(CRLF);
            chunkingBuffer.flip();
            state |= FLAG_WRITTEN_FIRST_CHUNK;

            int chunkingSize = chunkingBuffer.remaining();
            final ByteBuffer[] buf = new ByteBuffer[]{chunkingBuffer, src};
            long result = next.write(buf, 0, buf.length);
            chunkleft = src.remaining();
            if (result < chunkingSize) {
                return 0;
            } else {
                return (int) (result - chunkingSize);
            }
        } else {
            int oldLimit = src.limit();
            if (src.remaining() > chunkleft) {
                src.limit(chunkleft + src.position());
            }
            try {
                int chunkingSize = chunkingBuffer.remaining();
                if (chunkingSize > 0) {
                    final ByteBuffer[] buf = new ByteBuffer[]{chunkingBuffer, src};
                    int origialRemaining = src.remaining();
                    long result = next.write(buf, 0, buf.length);
                    int srcWritten = origialRemaining - src.remaining();
                    chunkleft -= srcWritten;
                    if (result < chunkingSize) {
                        return 0;
                    } else {
                        return (int) (result - chunkingSize);
                    }
                } else {
                    int result = next.write(src);
                    chunkleft -= result;
                    return result;
                }
            } finally {
                src.limit(oldLimit);
            }
        }
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
            if (anyAreSet(state, FLAG_NEXT_SHUTDWON)) {
                return next.flush();
            } else {
                if(trailerBuffer == null) {
                    next.write(chunkingBuffer);
                } else {
                    next.write(new ByteBuffer[]{chunkingBuffer, trailerBuffer}, 0, 2);
                }
                if (!chunkingBuffer.hasRemaining() && (trailerBuffer == null || !trailerBuffer.hasRemaining())) {
                    trailerBuffer = null;
                    try {
                        if(anyAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                            next.terminateWrites();
                        }
                        state |= FLAG_NEXT_SHUTDWON;
                        return next.flush();
                    } finally {
                        if(finishListener != null) {
                            finishListener.handleEvent(this);
                        }
                    }
                } else {
                    return false;
                }
            }
        } else {
            return next.flush();
        }
    }

    @Override
    public void terminateWrites() throws IOException {
        if (this.chunkleft != 0) {
            throw UndertowMessages.MESSAGES.chunkedChannelClosedMidChunk();
        }
        chunkingBuffer.clear();
        if(!anyAreSet(state, FLAG_WRITTEN_FIRST_CHUNK)) {
            //if no data was actually sent we just remove the transfer encoding header, and set content length 0
            //TODO: is this the best way to do it?
            //todo: should we make this behaviour configurable?
            responseHeaders.put(Headers.CONTENT_LENGTH, "0"); //according to the spec we don't actually need this, but better to be safe
            responseHeaders.remove(Headers.TRANSFER_ENCODING);
            state |= FLAG_NEXT_SHUTDWON | FLAG_WRITES_SHUTDOWN;
            next.terminateWrites();
            return;
        } else {
            chunkingBuffer.put(CRLF);
        }

        chunkingBuffer.put(LAST_CHUNK);
        HeaderMap trailers = attachable.getAttachment(TRAILERS);
        if(trailers != null && trailers.size() != 0) {
            StringBuilder sb = new StringBuilder();
            for(HeaderValues trailer : trailers) {
                for(String val : trailer) {
                    sb.append(trailer.getHeaderName().toString());
                    sb.append(": ");
                    sb.append(val);
                    sb.append("\r\n");
                }
            }
            sb.append("\r\n");
            //TODO: we should really use a pooled buffer here, but this generally only be a tiny buffer that is rarely used
            trailerBuffer = ByteBuffer.wrap(sb.toString().getBytes("US-ASCII"));
        } else {
            chunkingBuffer.put(CRLF);
        }
        chunkingBuffer.flip();
        state |= FLAG_WRITES_SHUTDOWN;
    }

    @Override
    public void awaitWritable() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        next.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        next.awaitWritable(time, timeUnit);
    }
}
