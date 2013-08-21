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

import io.undertow.conduits.ConduitListener;
import io.undertow.server.ExchangeCookieUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
final class AjpServerResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private static final Logger log = Logger.getLogger("io.undertow.server.channel.ajp.response");

    private static final int MAX_DATA_SIZE = 8186;

    private static final Map<HttpString, Integer> HEADER_MAP;

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

    private static final int FLAG_START = 1; //indicates that the header has not been generated yet.
    private static final int FLAG_SHUTDOWN = 1 << 2;
    private static final int FLAG_DELEGATE_SHUTDOWN = 1 << 3;
    private static final int FLAG_CLOSE_QUEUED = 1 << 4;
    private static final int FLAG_WRITE_RESUMED = 1 << 5;
    private static final int FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER = 1 << 6;

    private final Pool<ByteBuffer> pool;

    /**
     * State flags
     */
    private int state = FLAG_START;

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

    private final ConduitListener<? super AjpServerResponseConduit> finishListener;

    private final boolean headRequest;


    /**
     * An AJP request channel that wants access to the underlying sink channel.
     * <p/>
     * When this is then then any remaining data will be written out, and then ownership of
     * the underlying channel will be transferred to the request channel.
     * <p/>
     * While this field is set attempts to write will always return 0.
     */
    private ByteBuffer readBodyChunkBuffer;

    AjpServerResponseConduit(final StreamSinkConduit next, final Pool<ByteBuffer> pool, final HttpServerExchange exchange, ConduitListener<? super AjpServerResponseConduit> finishListener, boolean headRequest) {
        super(next);
        this.pool = pool;
        this.exchange = exchange;
        this.finishListener = finishListener;
        this.headRequest = headRequest;
        state = FLAG_START;
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
        if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
            return true;
        }
        int oldState = this.state;
        //if currentDataBuffer is set then we just
        if (anyAreSet(oldState, FLAG_START)) {
            if (readBodyChunkBuffer == null) {

                //merge the cookies into the header map
                ExchangeCookieUtils.flattenCookies(exchange);

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

                int headers = 0;
                //we need to count the headers
                final HeaderMap responseHeaders = exchange.getResponseHeaders();
                for (HttpString name : responseHeaders.getHeaderNames()) {
                    headers += responseHeaders.get(name).size();
                }

                putInt(buffer, headers);


                for (final HttpString header : responseHeaders.getHeaderNames()) {
                    for (String headerValue : responseHeaders.get(header)) {
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
                state &= ~FLAG_START;
            } else {
                //otherwise we just write out the get request body chunk and return
                ByteBuffer readBuffer = readBodyChunkBuffer;
                do {
                    int res = next.write(readBuffer);
                    if (res == 0) {
                        return false;
                    }
                } while (readBodyChunkBuffer.hasRemaining());
                readBodyChunkBuffer = null;
                this.state &= ~FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER; //clear the write entered flag
                return true;
            }
        }

        if (currentDataBuffer != null) {
            if (!writeCurrentBuffer()) {
                return false;
            }
        }

        //now next writing to the active request channel, so it can send
        //its messages
        ByteBuffer readBuffer = readBodyChunkBuffer;
        if (readBuffer != null) {
            do {
                int res = next.write(readBuffer);
                if (res == 0) {
                    return false;
                }
            } while (readBodyChunkBuffer.hasRemaining());
            readBodyChunkBuffer = null;
            this.state &= ~FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER;
        }

        if (anyAreSet(state, FLAG_SHUTDOWN) && allAreClear(state, FLAG_CLOSE_QUEUED)) {
            this.state |= FLAG_CLOSE_QUEUED;
            currentDataBuffer = pool.allocate();
            final ByteBuffer buffer = currentDataBuffer.getResource();
            packetHeaderAndDataBuffer = new ByteBuffer[1];
            packetHeaderAndDataBuffer[0] = buffer;
            buffer.put((byte) 'A');
            buffer.put((byte) 'B');
            buffer.put((byte) 0);
            buffer.put((byte) 2);
            buffer.put((byte) 5);
            buffer.put((byte) (exchange.isPersistent() ? 1 : 0)); //reuse
            buffer.flip();
            if (!writeCurrentBuffer()) {
                return false;
            }
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
            r = next.write(this.packetHeaderAndDataBuffer, 0, this.packetHeaderAndDataBuffer.length);
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
        if (headRequest) {
            int remaining = src.remaining();
            src.position(src.position() + remaining);
            return remaining;
        }
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
            final int originalPayloadSize = writeSize;
            int total = 0;
            long r = 0;
            do {
                r = next.write(buffers, 0, buffers.length);
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

                    return originalPayloadSize;
                }
            } while (toWrite > 0);
            return originalPayloadSize;
        } finally {
            src.limit(limit);
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
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    public boolean flush() throws IOException {
        if (!processWrite()) {
            return false;
        }
        int state = this.state;
        if (allAreSet(state, FLAG_SHUTDOWN) && allAreClear(state, FLAG_DELEGATE_SHUTDOWN)) {
            if (!exchange.isPersistent()) {
                next.terminateWrites();
            }
            if (finishListener != null) {
                finishListener.handleEvent(this);
            }
            this.state |= FLAG_DELEGATE_SHUTDOWN;
        }
        return next.flush();
    }

    @Override
    public void setWriteReadyHandler(WriteReadyHandler handler) {
        next.setWriteReadyHandler(new AjpServerWriteReadyHandler(handler));
    }

    public void suspendWrites() {
        log.trace("suspend");
        state &= ~FLAG_WRITE_RESUMED;
        if (allAreClear(state, FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER)) {
            next.suspendWrites();
        }
    }

    public void resumeWrites() {
        log.trace("resume");
        state |= FLAG_WRITE_RESUMED;
        next.resumeWrites();
    }

    public boolean isWriteResumed() {
        return anyAreSet(state, FLAG_WRITE_RESUMED);
    }

    public void wakeupWrites() {
        log.trace("wakeup");
        state |= FLAG_WRITE_RESUMED;
        next.wakeupWrites();
    }

    public void terminateWrites() throws IOException {
        if (anyAreSet(this.state, FLAG_SHUTDOWN)) {
            return;
        }
        this.state |= FLAG_SHUTDOWN;
        if (allAreClear(state, FLAG_START) &&
                readBodyChunkBuffer == null &&
                packetHeaderAndDataBuffer == null) {
            if (!exchange.isPersistent()) {
                next.terminateWrites();
            }
            if (finishListener != null) {
                finishListener.handleEvent(this);
            }
            this.state |= FLAG_DELEGATE_SHUTDOWN;
        }
    }

    public boolean doGetRequestBodyChunk(ByteBuffer buffer, final AjpServerRequestConduit requestChannel) throws IOException {
        this.readBodyChunkBuffer = buffer;
        boolean result = processWrite();
        if (!result) {
            //write it out in a listener
            this.state |= FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER;
            next.resumeWrites();
        }

        return result;
    }

    private final class AjpServerWriteReadyHandler implements WriteReadyHandler {

        private final WriteReadyHandler delegate;

        private AjpServerWriteReadyHandler(WriteReadyHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeReady() {
            if (anyAreSet(state, FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER)) {
                try {
                    boolean result = processWrite();
                } catch (IOException e) {
                    //TODO: figure out error handling for this
                    //I don't know if it actually needs any, as the
                    //reader should error out anyway.
                }
            }
            if (anyAreSet(state, FLAG_WRITE_RESUMED)) {
                delegate.writeReady();
            } else {
                suspendWrites();
            }
        }

        @Override
        public void forceTermination() {
            delegate.forceTermination();
        }

        @Override
        public void terminated() {
            delegate.terminated();
        }
    }

}
