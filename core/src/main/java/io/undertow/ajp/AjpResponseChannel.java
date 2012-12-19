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

package io.undertow.ajp;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * AJP response channel. For now we are going to assume that the buffers are sized to
 * fit complete packets. As AJP packets are limited to 8k this is a reasonable assumption.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
final class AjpResponseChannel implements StreamSinkChannel {

    private static final Logger log = Logger.getLogger("io.undertow.server.channel.ajp.response");

    private static final int MAX_DATA_SIZE = 8186;

    private static final Map<HttpString, Integer> HEADER_MAP;


    private final StreamSinkChannel delegate;
    private final Pool<ByteBuffer> pool;

    /**
     * State flags
     */
    @SuppressWarnings("unused")
    private volatile int state = FLAG_START;

    private static final AtomicIntegerFieldUpdater<AjpResponseChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(AjpResponseChannel.class, "state");

    /**
     * The current data buffer. This will be released once it has been written out.
     */
    private Pooled<ByteBuffer> currentDataBuffer;
    /**
     * The current packet header and data buffer combined, in a form that allows them to be written out
     * in a gathering write.
     */
    private ByteBuffer[] packetHeaderAndDataBuffer;

    private final HttpServerExchange exchange;


    /**
     * An AJP request channel that wants access to the underlying sink channel.
     * <p/>
     * When this is then then any remaining data will be written out, and then ownership of
     * the underlying channel will be transferred to the request channel.
     * <p/>
     * While this field is set attempts to write will always return 0.
     */
    private volatile ByteBuffer readBodyChunkBuffer;

    private boolean writesResumed = false;

    private final ChannelListener.SimpleSetter<AjpResponseChannel> writeSetter = new ChannelListener.SimpleSetter<AjpResponseChannel>();
    private final ChannelListener.SimpleSetter<AjpResponseChannel> closeSetter = new ChannelListener.SimpleSetter<AjpResponseChannel>();

    private static final int FLAG_START = 1; //indicates that the header has not been generated yet.
    private static final int FLAG_SHUTDOWN = 1 << 2;
    private static final int FLAG_DELEGATE_SHUTDOWN = 1 << 3;
    private static final int FLAG_CLOSE_QUEUED = 1 << 4;
    private static final int FLAG_WRITE_ENTERED = 1 << 5;

    static {
        final Map<HttpString, Integer> headers = new HashMap<HttpString, Integer>();
        headers.put(Headers.CONTENT_TYPE, 0xA001);
        headers.put(Headers.CONTENT_LANGUAGE, 0xA002);
        headers.put(Headers.CONTENT_LENGTH, 0xA003);
        headers.put(Headers.DATE, 0xA004);
        headers.put(Headers.LAST_MODIFIED, 0xA005);
        headers.put(Headers.LOCATION, 0xA006);
        headers.put(Headers.SET_COOKIE, 0xA007);
        headers.put(Headers.SET_COOKIE2, 0xA008);
        headers.put(Headers.SERVLET_ENGINE, 0xA009);
        headers.put(Headers.STATUS, 0xA00A);
        headers.put(Headers.WWW_AUTHENTICATE, 0xA00B);
        HEADER_MAP = Collections.unmodifiableMap(headers);
    }

    AjpResponseChannel(final StreamSinkChannel delegate, final Pool<ByteBuffer> pool, final HttpServerExchange exchange) {
        this.delegate = delegate;
        this.pool = pool;
        this.exchange = exchange;
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
        state = FLAG_START;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    private void putInt(final ByteBuffer buf, int value) {
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) (value & 0xFF));
    }

    private void putString(final ByteBuffer buf, String value) {
        final int length = value.length();
        putInt(buf, length);
        for (int i = 0; i < length; ++i) {
            buf.put((byte) value.charAt(i));
        }
        buf.put((byte) 0);
    }

    /**
     * Handles writing out the header data, plus any current buffers. Returns true if the write can proceed,
     * false if there are still cached bufers
     *
     * @return
     * @throws java.io.IOException
     */
    private boolean processWrite() throws IOException {
        int oldState;
        int writeEnteredState;
        do {
            oldState = this.state;
            if ((oldState & FLAG_WRITE_ENTERED) != 0) {
                return false;
            }
            if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
                return true;
            }
            writeEnteredState = oldState | FLAG_WRITE_ENTERED;
        } while (!stateUpdater.compareAndSet(this, state, writeEnteredState));
        int newState = writeEnteredState;

        if (anyAreSet(oldState, FLAG_START)) {
            currentDataBuffer = pool.allocate();
            final ByteBuffer buffer = currentDataBuffer.getResource();
            packetHeaderAndDataBuffer = new ByteBuffer[1];
            packetHeaderAndDataBuffer[0] = buffer;
            buffer.put((byte) 'A');
            buffer.put((byte) 'B');
            buffer.put((byte) 0); //we fill the size in later
            buffer.put((byte) 0);
            buffer.put((byte) 4);
            putInt(buffer, exchange.getResponseCode());
            putString(buffer, StatusCodes.getReason(exchange.getResponseCode()));
            putInt(buffer, exchange.getResponseHeaders().getHeaderNames().size());
            for (final HttpString header : exchange.getResponseHeaders()) {
                for (String headerValue : exchange.getResponseHeaders().get(header)) {
                    Integer headerCode = HEADER_MAP.get(header);
                    if (headerCode != null) {
                        putInt(buffer, headerCode);
                    } else {
                        putString(buffer, header.toString());
                    }
                    putString(buffer, headerValue);
                }
            }

            int dataLength = buffer.position() - 4;
            buffer.put(2, (byte) ((dataLength >> 8) & 0xFF));
            buffer.put(3, (byte) (dataLength & 0xFF));
            buffer.flip();
            newState = (newState & ~FLAG_START);
        }

        if (currentDataBuffer != null) {
            if (!writeCurrentBuffer()) {
                stateUpdater.set(this, newState & ~FLAG_WRITE_ENTERED); //clear the write entered flag
                return false;
            }
        }

        //now delegate writing to the active request channel, so it can send
        //its messages
        ByteBuffer readBuffer = readBodyChunkBuffer;
        if (readBuffer != null) {
            do {
                int res = delegate.write(readBuffer);
                if (res == 0) {
                    stateUpdater.set(this, newState & ~FLAG_WRITE_ENTERED); //clear the write entered flag
                    return false;
                }
            } while (readBodyChunkBuffer.hasRemaining());
            readBodyChunkBuffer = null;
        }

        if (anyAreSet(state, FLAG_SHUTDOWN) && allAreClear(state, FLAG_CLOSE_QUEUED)) {
            newState = newState | FLAG_CLOSE_QUEUED;
            currentDataBuffer = pool.allocate();
            final ByteBuffer buffer = currentDataBuffer.getResource();
            packetHeaderAndDataBuffer = new ByteBuffer[1];
            packetHeaderAndDataBuffer[0] = buffer;
            buffer.put((byte) 0x12);
            buffer.put((byte) 0x34);
            buffer.put((byte) 0);
            buffer.put((byte) 2);
            buffer.put((byte) 5);
            buffer.put((byte) 0); //reuse
            buffer.flip();
            if (!writeCurrentBuffer()) {
                stateUpdater.set(this, newState & ~FLAG_WRITE_ENTERED); //clear the write entered flag
                return false;
            }
        }
        if (newState != writeEnteredState) {
            stateUpdater.set(this, newState);
        }
        return true;
    }

    private boolean writeCurrentBuffer() throws IOException {
        long toWrite = 0;
        for (ByteBuffer b : this.packetHeaderAndDataBuffer) {
            toWrite += b.remaining();
        }
        long r = 0;
        do {
            r = delegate.write(this.packetHeaderAndDataBuffer);
            if (r == -1) {
                throw new ClosedChannelException();
            } else if (r == 0) {
                return false;
            }
            toWrite -= r;
        } while (toWrite > 0);
        currentDataBuffer.free();
        this.currentDataBuffer = null;
        return true;
    }


    public int write(final ByteBuffer src) throws IOException {
        if (!processWrite()) {
            return 0;
        }
        try {
            int limit = src.limit();
            try {
                if (src.remaining() > MAX_DATA_SIZE) {
                    src.limit(src.position() + MAX_DATA_SIZE);
                }
                final int writeSize = src.remaining();
                final ByteBuffer[] buffers = createHeader(src);
                int toWrite = 0;
                for (ByteBuffer buffer : buffers) {
                    toWrite += buffer.remaining();
                }
                int total = 0;
                long r = 0;
                do {
                    r = delegate.write(buffers);
                    total += r;
                    toWrite -= r;
                    if (r == -1) {
                        throw new ClosedChannelException();
                    } else if (r == 0) {
                        //we need to copy all the remaining bytes
                        Pooled<ByteBuffer> newPooledBuffer = pool.allocate();
                        while (src.hasRemaining()) {
                            newPooledBuffer.getResource().put(src);
                        }
                        newPooledBuffer.getResource().flip();
                        ByteBuffer[] savedBuffers = new ByteBuffer[3];
                        savedBuffers[0] = buffers[0];
                        savedBuffers[1] = newPooledBuffer.getResource();
                        savedBuffers[2] = buffers[2];
                        this.packetHeaderAndDataBuffer = savedBuffers;
                        this.currentDataBuffer = newPooledBuffer;

                        return writeSize;
                    }
                } while (toWrite > 0);
                return total;
            } finally {
                src.limit(limit);
            }
        } finally {
            exitWrite();
        }

    }

    private ByteBuffer[] createHeader(final ByteBuffer src) {
        int remaining = src.remaining();
        int chunkSize = remaining + 4;
        byte[] header = new byte[7];
        header[0] = (byte) 'A';
        header[1] = (byte) 'B';
        header[2] = (byte) ((chunkSize >> 8) & 0xFF);
        header[3] = (byte) (chunkSize & 0xFF);
        header[4] = (byte) (3 & 0xFF);
        header[5] = (byte) ((remaining >> 8) & 0xFF);
        header[6] = (byte) (remaining & 0xFF);

        byte[] footer = new byte[1];
        footer[0] = 0;

        final ByteBuffer[] buffers = new ByteBuffer[3];
        buffers[0] = ByteBuffer.wrap(header);
        buffers[1] = src;
        buffers[2] = ByteBuffer.wrap(footer);
        return buffers;
    }

    private void exitWrite() {
        stateUpdater.set(this, state & ~FLAG_WRITE_ENTERED);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; ++i) {
            while (srcs[i].hasRemaining()) {
                int written = write(srcs[i]);
                if (written <= 0 && total == 0) {
                    return written;
                } else if (written <= 0) {
                    return total;
                }
                total += written;
            }
        }
        return total;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    public boolean flush() throws IOException {
        if (!processWrite()) {
            return false;
        }
        try {
            int state = this.state;
            if (allAreSet(state, FLAG_SHUTDOWN) && allAreClear(state, FLAG_DELEGATE_SHUTDOWN)) {
                delegate.shutdownWrites();
                stateUpdater.set(this, state | FLAG_DELEGATE_SHUTDOWN);
            }
            return delegate.flush();
        } finally {
            exitWrite();
        }
    }

    public void suspendWrites() {
        log.trace("suspend");
        delegate.suspendWrites();
    }

    public void resumeWrites() {
        log.trace("resume");
        delegate.resumeWrites();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        log.trace("wakeup");
        delegate.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        int oldState = 0, newState = 0;
        do {
            oldState = this.state;
            if (anyAreSet(oldState, FLAG_SHUTDOWN)) {
                return;
            }
            newState = oldState | FLAG_SHUTDOWN;
        } while (!stateUpdater.compareAndSet(this, oldState, newState));
        if (allAreClear(oldState, FLAG_START) &&
                readBodyChunkBuffer == null &&
                packetHeaderAndDataBuffer == null) {
            delegate.shutdownWrites();
            newState |= FLAG_DELEGATE_SHUTDOWN;
            while (stateUpdater.compareAndSet(this, oldState, newState)) {
                oldState = state;
                newState = oldState | FLAG_DELEGATE_SHUTDOWN;
            }
        }
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    public boolean doGetRequestBodyChunk(ByteBuffer buffer, final AjpRequestChannel requestChannel) throws IOException {
        this.readBodyChunkBuffer = buffer;
        boolean result = processWrite();
        if (result) {
            exitWrite();
        } else {
            //if this write does not work we spawn a thread to force it out.
            //this is not great, but there is not really a great deal we can do here
            //there is probably a better way to deal with this, but I am not really sure what it is
            this.exchange.getConnection().getWorker().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (AjpResponseChannel.this.readBodyChunkBuffer != null) {
                            delegate.awaitWritable();
                            boolean result = processWrite();
                            if (result) {
                                exitWrite();
                            }
                        }
                    } catch (IOException e) {
                        if (requestChannel.isReadResumed()) {
                            requestChannel.wakeupReads();
                        }
                        if (isWriteResumed()) {
                            delegate.wakeupWrites();
                        }
                        UndertowLogger.REQUEST_LOGGER.debug("Error writing get request body chunk");
                    }
                }
            });
        }

        return result;
    }
}
