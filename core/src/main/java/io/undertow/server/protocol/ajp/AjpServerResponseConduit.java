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

package io.undertow.server.protocol.ajp;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.AbstractFramedStreamSinkConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;
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
import static org.xnio.Bits.anyAreSet;

/**
 * AJP response channel. For now we are going to assume that the buffers are sized to
 * fit complete packets. As AJP packets are limited to 8k this is a reasonable assumption.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
final class AjpServerResponseConduit extends AbstractFramedStreamSinkConduit {

    private static final Logger log = Logger.getLogger("io.undertow.server.channel.ajp.response");

    private static final int DEFAULT_MAX_DATA_SIZE = 8192;

    private static final Map<HttpString, Integer> HEADER_MAP;

    private static final ByteBuffer FLUSH_PACKET = ByteBuffer.allocateDirect(8);

    static {
        final Map<HttpString, Integer> headers = new HashMap<>();
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

        FLUSH_PACKET.put((byte) 'A');
        FLUSH_PACKET.put((byte) 'B');
        FLUSH_PACKET.put((byte) 0);
        FLUSH_PACKET.put((byte) 4);
        FLUSH_PACKET.put((byte) 3);
        FLUSH_PACKET.put((byte) 0);
        FLUSH_PACKET.put((byte) 0);
        FLUSH_PACKET.put((byte) 0);
        FLUSH_PACKET.flip();
    }

    private static final int FLAG_START = 1; //indicates that the header has not been generated yet.
    private static final int FLAG_WRITE_RESUMED = 1 << 2;
    private static final int FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER = 1 << 3;
    private static final int FLAG_WRITE_SHUTDOWN = 1 << 4;
    private static final int FLAG_READS_DONE = 1 << 5;
    private static final int FLAG_FLUSH_QUEUED = 1 << 6;

    private static final ByteBuffer CLOSE_FRAME_PERSISTENT;
    private static final ByteBuffer CLOSE_FRAME_NON_PERSISTENT;

    static {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[6]);
        buffer.put((byte) 'A');
        buffer.put((byte) 'B');
        buffer.put((byte) 0);
        buffer.put((byte) 2);
        buffer.put((byte) 5);
        buffer.put((byte) 1); //reuse
        buffer.flip();
        CLOSE_FRAME_PERSISTENT = buffer;
        buffer = ByteBuffer.wrap(new byte[6]);
        buffer.put(CLOSE_FRAME_PERSISTENT.duplicate());
        buffer.put(5, (byte) 0);
        buffer.flip();
        CLOSE_FRAME_NON_PERSISTENT = buffer;
    }


    private final ByteBufferPool pool;

    /**
     * State flags
     */
    private int state = FLAG_START;

    private final HttpServerExchange exchange;

    private final ConduitListener<? super AjpServerResponseConduit> finishListener;

    private final boolean headRequest;

    AjpServerResponseConduit(final StreamSinkConduit next, final ByteBufferPool pool, final HttpServerExchange exchange, ConduitListener<? super AjpServerResponseConduit> finishListener, boolean headRequest) {
        super(next);
        this.pool = pool;
        this.exchange = exchange;
        this.finishListener = finishListener;
        this.headRequest = headRequest;
        state = FLAG_START;
    }

    private static void putInt(final ByteBuffer buf, int value) {
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) (value & 0xFF));
    }

    private static void putString(final ByteBuffer buf, String value) {
        final int length = value.length();
        putInt(buf, length);
        for (int i = 0; i < length; ++i) {
            char c = value.charAt(i);
            if(c != '\r' && c != '\n'){
                buf.put((byte) c);
            } else {
                buf.put((byte)' ');
            }
        }
        buf.put((byte) 0);
    }

    private void putHttpString(final ByteBuffer buf, HttpString value) {
        final int length = value.length();
        putInt(buf, length);
        value.appendTo(buf);
        buf.put((byte) 0);
    }

    /**
     * Handles generating the header if required, and adding it to the frame queue.
     *
     * No attempt is made to actually flush this, so a gathering write can be used to actually flush the data
     */
    private void processAJPHeader() {
        int oldState = this.state;
        if (anyAreSet(oldState, FLAG_START)) {

            PooledByteBuffer[] byteBuffers = null;

            //merge the cookies into the header map
            Connectors.flattenCookies(exchange);

            PooledByteBuffer pooled = pool.allocate();
            ByteBuffer buffer = pooled.getBuffer();
            buffer.put((byte) 'A');
            buffer.put((byte) 'B');
            buffer.put((byte) 0); //we fill the size in later
            buffer.put((byte) 0);
            buffer.put((byte) 4);
            putInt(buffer, exchange.getStatusCode());
            String reason = exchange.getReasonPhrase();
            if(reason == null) {
                reason = StatusCodes.getReason(exchange.getStatusCode());
            }
            if(reason.length() + 4 > buffer.remaining()) {
                pooled.close();
                throw UndertowMessages.MESSAGES.reasonPhraseToLargeForBuffer(reason);
            }
            putString(buffer, reason);

            int headers = 0;
            //we need to count the headers
            final HeaderMap responseHeaders = exchange.getResponseHeaders();
            for (HttpString name : responseHeaders.getHeaderNames()) {
                headers += responseHeaders.get(name).size();
            }

            putInt(buffer, headers);


            for (final HttpString header : responseHeaders.getHeaderNames()) {
                for (String headerValue : responseHeaders.get(header)) {
                    if(buffer.remaining() < header.length() + headerValue.length() + 6) {
                        //if there is not enough room in the buffer we need to allocate more
                        buffer.flip();
                        if(byteBuffers == null) {
                            byteBuffers = new PooledByteBuffer[2];
                            byteBuffers[0] = pooled;
                        } else {
                            PooledByteBuffer[] old = byteBuffers;
                            byteBuffers = new PooledByteBuffer[old.length + 1];
                            System.arraycopy(old, 0, byteBuffers, 0, old.length);
                        }
                        pooled = pool.allocate();
                        byteBuffers[byteBuffers.length - 1] = pooled;
                        buffer = pooled.getBuffer();
                    }

                    Integer headerCode = HEADER_MAP.get(header);
                    if (headerCode != null) {
                        putInt(buffer, headerCode);
                    } else {
                        putHttpString(buffer, header);
                    }
                    putString(buffer, headerValue);
                }
            }
            if(byteBuffers == null) {
                int dataLength = buffer.position() - 4;
                buffer.put(2, (byte) ((dataLength >> 8) & 0xFF));
                buffer.put(3, (byte) (dataLength & 0xFF));
                buffer.flip();
                queueFrame(new PooledBufferFrameCallback(pooled), buffer);
            } else {
                ByteBuffer[] bufs = new ByteBuffer[byteBuffers.length];
                for(int i = 0; i < bufs.length; ++i) {
                    bufs[i] = byteBuffers[i].getBuffer();
                }
                int dataLength = (int) (Buffers.remaining(bufs) - 4);
                bufs[0].put(2, (byte) ((dataLength >> 8) & 0xFF));
                bufs[0].put(3, (byte) (dataLength & 0xFF));
                buffer.flip();
                queueFrame(new PooledBuffersFrameCallback(byteBuffers), bufs);
            }
            state &= ~FLAG_START;
        }
    }


    @Override
    protected void queueCloseFrames() {
        processAJPHeader();
        final ByteBuffer buffer = exchange.isPersistent() ? CLOSE_FRAME_PERSISTENT.duplicate() : CLOSE_FRAME_NON_PERSISTENT.duplicate();
        queueFrame(null, buffer);
    }

    public int write(final ByteBuffer src) throws IOException {
        if(queuedDataLength() > 0) {
            //if there is data in the queue we flush and return
            //otherwise the queue can grow indefinitely
            if(!flushQueuedData()) {
                return 0;
            }
        }
        processAJPHeader();
        if (headRequest) {
            int remaining = src.remaining();
            src.position(src.position() + remaining);
            return remaining;
        }
        int limit = src.limit();
        try {
            int maxData = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_AJP_PACKET_SIZE, DEFAULT_MAX_DATA_SIZE) - 8;
            if (src.remaining() > maxData) {
                src.limit(src.position() + maxData);
            }
            final int writeSize = src.remaining();
            final ByteBuffer[] buffers = createHeader(src);
            int toWrite = 0;
            for (ByteBuffer buffer : buffers) {
                toWrite += buffer.remaining();
            }
            final int originalPayloadSize = writeSize;
            long r = 0;
            do {
                r = super.write(buffers, 0, buffers.length);
                toWrite -= r;
                if (r == -1) {
                    throw new ClosedChannelException();
                } else if (r == 0) {
                    //we need to copy all the remaining bytes
                    //TODO: this assumes the buffer is big enough
                    PooledByteBuffer newPooledBuffer = pool.allocate();
                    while (src.hasRemaining()) {
                        newPooledBuffer.getBuffer().put(src);
                    }
                    newPooledBuffer.getBuffer().flip();
                    ByteBuffer[] savedBuffers = new ByteBuffer[3];
                    savedBuffers[0] = buffers[0];
                    savedBuffers[1] = newPooledBuffer.getBuffer();
                    savedBuffers[2] = buffers[2];
                    queueFrame(new PooledBufferFrameCallback(newPooledBuffer), savedBuffers);
                    return originalPayloadSize;
                }
            } while (toWrite > 0);
            return originalPayloadSize;
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
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
                if (written == 0) {
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

    @Override
    protected void finished() {
        if (finishListener != null) {
            finishListener.handleEvent(this);
        }
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

    public boolean flush() throws IOException {
        processAJPHeader();
        if(allAreClear(state, FLAG_FLUSH_QUEUED) && !isWritesTerminated()) {
            queueFrame(new FrameCallBack() {
                @Override
                public void done() {
                    state &= ~FLAG_FLUSH_QUEUED;
                }

                @Override
                public void failed(IOException e) {

                }
            }, FLUSH_PACKET.duplicate());
            state |= FLAG_FLUSH_QUEUED;
        }
        return flushQueuedData();
    }
    public boolean isWriteResumed() {
        return anyAreSet(state, FLAG_WRITE_RESUMED);
    }

    public void wakeupWrites() {
        log.trace("wakeup");
        state |= FLAG_WRITE_RESUMED;
        next.wakeupWrites();
    }

    @Override
    protected void doTerminateWrites() throws IOException {
        try {
            if (!exchange.isPersistent()) {
                next.terminateWrites();
            }
            state |= FLAG_WRITE_SHUTDOWN;
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public boolean isWriteShutdown() {
        return super.isWriteShutdown() || anyAreSet(state, FLAG_WRITE_SHUTDOWN);
    }

    boolean doGetRequestBodyChunk(ByteBuffer buffer, final AjpServerRequestConduit requestChannel) throws IOException {
        //first attempt to just write out the buffer
        //if there are other frames queued they will be written out first
        if(isWriteShutdown()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        super.write(buffer);
        if (buffer.hasRemaining()) {
            //write it out in a listener
            this.state |= FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER;
            queueFrame(new FrameCallBack() {

                @Override
                public void done() {
                    state &= ~FLAG_WRITE_READ_BODY_CHUNK_FROM_LISTENER;
                    if (allAreClear(state, FLAG_WRITE_RESUMED)) {
                        next.suspendWrites();
                    }
                }

                @Override
                public void failed(IOException e) {
                    requestChannel.setReadBodyChunkError(e);
                }
            }, buffer);
            next.resumeWrites();
            return false;
        }
        return true;
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
                    flushQueuedData();
                } catch (IOException e) {
                    log.debug("Error flushing when doing async READ_BODY_CHUNK flush", e);
                }
            }
            if (anyAreSet(state, FLAG_WRITE_RESUMED)) {
                delegate.writeReady();
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
