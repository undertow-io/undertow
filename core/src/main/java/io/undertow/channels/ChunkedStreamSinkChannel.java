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

package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowMessages;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.ChannelListeners.invokeChannelListener;

/**
 * Channel that implements HTTP chunked transfer coding.
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSinkChannel extends DelegatingStreamSinkChannel<ChunkedStreamSinkChannel> {

    private static final Logger log = Logger.getLogger(ChunkedStreamSinkChannel.class);

    private final ChannelListener<? super ChunkedStreamSinkChannel> finishListener;
    private final int config;

    private static final byte[] LAST_CHUNK = "0\r\n\r\n".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();

    private int state;
    private int chunkleft = 0;

    private final ByteBuffer chunkingBuffer = ByteBuffer.allocate(14); //14 is the most


    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    /**
     * Flag that is set when {@link #shutdownWrites()} or @{link #close()} is called
     */
    private static final int FLAG_WRITES_SHUTDOWN = 1;
    private static final int FLAG_DELEGATE_SHUTDWON = 1 << 2;
    private static final int FLAG_WRITING_CHUNK = 1 << 3;
    private static final int FLAG_WRITTEN_FIRST_CHUNK = 1 << 4;

    /**
     * Set when the finish listener has been invoked
     */
    private static final int FLAG_FINISH = 1 << 4;

    /**
     * Construct a new instance.
     *
     * @param delegate       the channel to wrap
     * @param configurable   {@code true} to allow configuration of the delegate channel, {@code false} otherwise
     * @param passClose      {@code true} to close the underlying channel when this channel is closed, {@code false} otherwise
     * @param finishListener
     */
    public ChunkedStreamSinkChannel(final StreamSinkChannel delegate, final boolean configurable, final boolean passClose, final ChannelListener<? super ChunkedStreamSinkChannel> finishListener) {
        super(delegate);
        this.finishListener = finishListener;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (passClose ? CONF_FLAG_PASS_CLOSE : 0);
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
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
            chunkingBuffer.put(Integer.toHexString(src.remaining()).getBytes());
            chunkingBuffer.put(CRLF);
            chunkingBuffer.flip();
            state |= FLAG_WRITTEN_FIRST_CHUNK;

            int chunkingSize = chunkingBuffer.remaining();
            final ByteBuffer[] buf = new ByteBuffer[]{chunkingBuffer, src};
            long result = delegate.write(buf);
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
                    long result = delegate.write(buf);
                    int srcWritten = origialRemaining - src.remaining();
                    chunkleft -= srcWritten;
                    if (result < chunkingSize) {
                        return 0;
                    } else {
                        return (int) (result - chunkingSize);
                    }
                } else {
                    int result = delegate.write(src);
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
        return src.transferTo(position, count, this);
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public boolean flush() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            if (anyAreSet(state, FLAG_DELEGATE_SHUTDWON)) {
                return delegate.flush();
            } else {
                delegate.write(chunkingBuffer);
                if (!chunkingBuffer.hasRemaining()) {
                    try {
                        if(anyAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                            delegate.shutdownWrites();
                        }
                        state |= FLAG_DELEGATE_SHUTDWON;
                        return delegate.flush();
                    } finally {
                        ChannelListeners.invokeChannelListener(this, finishListener);
                    }
                } else {
                    return false;
                }
            }
        } else {
            return delegate.flush();
        }
    }

    @Override
    public void shutdownWrites() throws IOException {
        if (this.chunkleft != 0) {
            throw UndertowMessages.MESSAGES.chunkedChannelClosedMidChunk();
        }
        chunkingBuffer.clear();
        if(anyAreSet(state, FLAG_WRITTEN_FIRST_CHUNK)) {
            chunkingBuffer.put(CRLF);
        }
        chunkingBuffer.put(LAST_CHUNK);
        chunkingBuffer.flip();
        state |= FLAG_WRITES_SHUTDOWN;
    }

    @Override
    public void close() throws IOException {
        try {
            if(anyAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                delegate.close();
            }
            if (anyAreClear(state, FLAG_DELEGATE_SHUTDWON)) {
                throw UndertowMessages.MESSAGES.closeCalledWithDataStillToBeFlushed();
            }
        } finally {
            invokeChannelListener(this, closeSetter.get());
        }
    }

    @Override
    public void awaitWritable() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public boolean isOpen() {
        return allAreClear(state, FLAG_DELEGATE_SHUTDWON) && delegate.isOpen();
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) && delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.getOption(option) : null;
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.setOption(option, value) : null;
    }
}
