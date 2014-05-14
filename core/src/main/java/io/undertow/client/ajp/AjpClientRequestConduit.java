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

package io.undertow.client.ajp;

import io.undertow.client.ClientRequest;
import io.undertow.client.ProxiedRequestAttachments;
import io.undertow.client.UndertowClientMessages;
import io.undertow.conduits.ConduitListener;
import io.undertow.util.FlexBase64;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.FixedLengthUnderflowException;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.undertow.util.Methods.ACL;
import static io.undertow.util.Methods.BASELINE_CONTROL;
import static io.undertow.util.Methods.CHECKIN;
import static io.undertow.util.Methods.CHECKOUT;
import static io.undertow.util.Methods.COPY;
import static io.undertow.util.Methods.DELETE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.LABEL;
import static io.undertow.util.Methods.LOCK;
import static io.undertow.util.Methods.MERGE;
import static io.undertow.util.Methods.MKACTIVITY;
import static io.undertow.util.Methods.MKCOL;
import static io.undertow.util.Methods.MKWORKSPACE;
import static io.undertow.util.Methods.MOVE;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.Methods.PROPFIND;
import static io.undertow.util.Methods.PROPPATCH;
import static io.undertow.util.Methods.PUT;
import static io.undertow.util.Methods.REPORT;
import static io.undertow.util.Methods.SEARCH;
import static io.undertow.util.Methods.TRACE;
import static io.undertow.util.Methods.UNCHECKOUT;
import static io.undertow.util.Methods.UNLOCK;
import static io.undertow.util.Methods.UPDATE;
import static io.undertow.util.Methods.VERSION_CONTROL;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * AJP client request channel. For now we are going to assume that the buffers are sized to
 * fit complete packets. As AJP packets are limited to 8k this is a reasonable assumption.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
final class AjpClientRequestConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private static final int MAX_DATA_SIZE = 8186;

    private static final Map<HttpString, Integer> HEADER_MAP;
    private static final Map<HttpString, Integer> HTTP_METHODS;

    private final Pool<ByteBuffer> pool;

    /**
     * The current data buffer. This will be released once it has been written out.
     */
    private Pooled<ByteBuffer> currentDataBuffer;

    /**
     * header buffer for the current chunk, if it was not written out
     */
    private ByteBuffer headerDataBuffer;

    private final AjpClientExchange exchange;

    private final ConduitListener<? super AjpClientRequestConduit> finishListener;

    private final boolean hasContent;
    /**
     * State flags, with the chunk remaining stored in the low bytes
     */
    private long state;

    private long totalRemaining;

    private int requestedChunkSize = -1;

    /**
     * The remaining bits are used to store the remaining chunk size.
     */
    private static final long STATE_MASK = longBitMask(0, 58);

    private static final long FLAG_START = 1L << 63L; //indicates that the header has not been generated yet.
    private static final long FLAG_SHUTDOWN = 1L << 62L;
    private static final long FLAG_DELEGATE_SHUTDOWN = 1L << 61L;
    private static final long FLAG_WRITES_RESUMED = 1L << 60L;
    private static final long FLAG_FINAL_CHUNK_GENERATED = 1L << 59L;

    static {
        final Map<HttpString, Integer> headers = new HashMap<HttpString, Integer>();
        headers.put(Headers.ACCEPT, 0xA001);
        headers.put(Headers.ACCEPT_CHARSET, 0xA002);
        headers.put(Headers.ACCEPT_ENCODING, 0xA003);
        headers.put(Headers.ACCEPT_LANGUAGE, 0xA004);
        headers.put(Headers.AUTHORIZATION, 0xA005);
        headers.put(Headers.CONNECTION, 0xA006);
        headers.put(Headers.CONTENT_TYPE, 0xA007);
        headers.put(Headers.CONTENT_LENGTH, 0xA008);
        headers.put(Headers.COOKIE, 0xA009);
        headers.put(Headers.COOKIE2, 0xA00A);
        headers.put(Headers.HOST, 0xA00B);
        headers.put(Headers.PRAGMA, 0xA00C);
        headers.put(Headers.REFERER, 0xA00D);
        headers.put(Headers.USER_AGENT, 0xA00E);

        HEADER_MAP = Collections.unmodifiableMap(headers);

        final Map<HttpString, Integer> methods = new HashMap<HttpString, Integer>();
        methods.put(OPTIONS, 1);
        methods.put(GET, 2);
        methods.put(HEAD, 3);
        methods.put(POST, 4);
        methods.put(PUT, 5);
        methods.put(DELETE, 6);
        methods.put(TRACE, 7);
        methods.put(PROPFIND, 8);
        methods.put(PROPPATCH, 9);
        methods.put(MKCOL, 10);
        methods.put(COPY, 11);
        methods.put(MOVE, 12);
        methods.put(LOCK, 13);
        methods.put(UNLOCK, 14);
        methods.put(ACL, 15);
        methods.put(REPORT, 16);
        methods.put(VERSION_CONTROL, 17);
        methods.put(CHECKIN, 18);
        methods.put(CHECKOUT, 19);
        methods.put(UNCHECKOUT, 20);
        methods.put(SEARCH, 21);
        methods.put(MKWORKSPACE, 22);
        methods.put(UPDATE, 23);
        methods.put(LABEL, 24);
        methods.put(MERGE, 25);
        methods.put(BASELINE_CONTROL, 26);
        methods.put(MKACTIVITY, 27);
        HTTP_METHODS = Collections.unmodifiableMap(methods);

    }

    AjpClientRequestConduit(final StreamSinkConduit next, final Pool<ByteBuffer> pool, final AjpClientExchange exchange, ConduitListener<? super AjpClientRequestConduit> finishListener, long size) {
        super(next);
        this.pool = pool;
        this.exchange = exchange;
        this.finishListener = finishListener;
        this.hasContent = size != 0;
        this.totalRemaining = size;
        state = FLAG_START;

        if (hasContent) {
            if (size > 0) {
                //fixed length
                requestedChunkSize = MAX_DATA_SIZE;
            } else {
                requestedChunkSize = 0;
            }
        }

    }

    private static void putInt(final ByteBuffer buf, int value) {
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) (value & 0xFF));
    }

    private static void putString(final ByteBuffer buf, String value) {
        final int length = value.length();
        putInt(buf, length);
        for (int i = 0; i < length; ++i) {
            buf.put((byte) value.charAt(i));
        }
        buf.put((byte) 0);
    }

    private static void putHttpString(final ByteBuffer buf, HttpString value) {
        final int length = value.length();
        putInt(buf, length);
        value.appendTo(buf);
        buf.put((byte) 0);
    }

    /**
     * Called when the target requests a body chunk
     * @param requestedSize The size of the requested chunk
     */
    void setBodyChunkRequested(int requestedSize) {
        this.requestedChunkSize = requestedSize;
        if (anyAreSet(state, FLAG_WRITES_RESUMED)) {
            next.resumeWrites();
        }
    }

    /**
     * Called then the request is done. This means no more chunks will be forthcoming,
     * and if the request has not been full written then the channel is closed.
     */
    void setRequestDone() {
        if(!anyAreSet(state, FLAG_SHUTDOWN)) {
            state |= FLAG_SHUTDOWN;
            if (anyAreSet(state, FLAG_WRITES_RESUMED)) {
                next.resumeWrites();
            }
        }
    }

    /**
     * Handles writing out the header data, plus any current buffers. Returns true if the write can proceed,
     * false if there are still cached buffers
     *
     * @throws java.io.IOException
     */
    private boolean processWrite() throws IOException {
        if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
            return true;
        }

        //if currentDataBuffer is set then we just
        if (anyAreSet(state, FLAG_START)) {
            this.state &= ~FLAG_START;

            final ClientRequest request = exchange.getRequest();
            final String path;
            final String queryString;
            int qsIndex = exchange.getRequest().getPath().indexOf('?');
            if (qsIndex == -1) {
                path = exchange.getRequest().getPath();
                queryString = null;
            } else {
                path = exchange.getRequest().getPath().substring(0, qsIndex);
                queryString = exchange.getRequest().getPath().substring(qsIndex + 1);
            }

            currentDataBuffer = pool.allocate();
            final ByteBuffer buffer = currentDataBuffer.getResource();
            buffer.put((byte) 0x12);
            buffer.put((byte) 0x34);
            buffer.put((byte) 0); //we fill the size in later
            buffer.put((byte) 0);
            buffer.put((byte) 2);
            final Integer methodNp = HTTP_METHODS.get(request.getMethod());
            if (methodNp == null) {
                throw UndertowClientMessages.MESSAGES.unknownMethod(request.getMethod());
            }
            buffer.put((byte) (int) methodNp);
            putHttpString(buffer, exchange.getRequest().getProtocol());
            putString(buffer, path);
            putString(buffer, notNull(request.getAttachment(ProxiedRequestAttachments.REMOTE_ADDRESS)));
            putString(buffer, notNull(request.getAttachment(ProxiedRequestAttachments.REMOTE_HOST)));
            putString(buffer, notNull(request.getAttachment(ProxiedRequestAttachments.SERVER_NAME)));
            putInt(buffer, notNull(request.getAttachment(ProxiedRequestAttachments.SERVER_PORT)));
            buffer.put((byte) (notNull(request.getAttachment(ProxiedRequestAttachments.IS_SSL)) ? 1 : 0));

            int headers = 0;
            //we need to count the headers
            final HeaderMap responseHeaders = request.getRequestHeaders();
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
                        putHttpString(buffer, header);
                    }
                    putString(buffer, headerValue);
                }
            }

            if (queryString != null) {
                buffer.put((byte) 5); //query_string
                putString(buffer, queryString);
            }

            String remoteUser = request.getAttachment(ProxiedRequestAttachments.REMOTE_USER);
            if(remoteUser != null) {
                buffer.put((byte) 3);
                putString(buffer, remoteUser);
            }
            String authType = request.getAttachment(ProxiedRequestAttachments.AUTH_TYPE);
            if(authType != null) {
                buffer.put((byte) 4);
                putString(buffer, authType);
            }
            String route = request.getAttachment(ProxiedRequestAttachments.ROUTE);
            if(route != null) {
                buffer.put((byte) 6);
                putString(buffer, route);
            }
            String sslCert = request.getAttachment(ProxiedRequestAttachments.SSL_CERT);
            if(sslCert != null) {
                buffer.put((byte) 7);
                putString(buffer, sslCert);
            }
            String sslCypher = request.getAttachment(ProxiedRequestAttachments.SSL_CYPHER);
            if(sslCypher != null) {
                buffer.put((byte) 8);
                putString(buffer, sslCypher);
            }
            byte[] sslSession = request.getAttachment(ProxiedRequestAttachments.SSL_SESSION_ID);
            if(sslSession != null) {
                buffer.put((byte) 9);
                putString(buffer, FlexBase64.encodeString(sslSession, false));
            }
            Integer sslKeySize = request.getAttachment(ProxiedRequestAttachments.SSL_KEY_SIZE);
            if(sslKeySize != null) {
                buffer.put((byte) 0xB);
                putString(buffer, sslKeySize.toString());
            }
            String secret = request.getAttachment(ProxiedRequestAttachments.SECRET);
            if(secret != null) {
                buffer.put((byte) 0xC);
                putString(buffer, secret);
            }
            String storedMethod = request.getAttachment(ProxiedRequestAttachments.STORED_METHOD);
            if(storedMethod != null) {
                buffer.put((byte) 0xD);
                putString(buffer, storedMethod);
            }
            buffer.put((byte) 0xFF);

            int dataLength = buffer.position() - 4;
            buffer.put(2, (byte) ((dataLength >> 8) & 0xFF));
            buffer.put(3, (byte) (dataLength & 0xFF));
            buffer.flip();
            if (!hasContent) {
                this.state |= FLAG_SHUTDOWN;
            }
        }

        if (currentDataBuffer != null) {
            if (!writeCurrentBuffer()) {
                return false;
            }
        }
        return true;
    }

    /**
     * generates a final chunk for non fixed length requests
     *
     * @return
     * @throws IOException
     */
    private boolean handleFinalChunk() throws IOException {
        if (!hasContent) {
            return true;
        }
        if (anyAreSet(state, FLAG_SHUTDOWN) && !anyAreSet(state, FLAG_FINAL_CHUNK_GENERATED)) {
            state |= FLAG_FINAL_CHUNK_GENERATED;

            if (totalRemaining < 0) {
                byte[] header = new byte[6];
                header[0] = (byte) 0x12;
                header[1] = (byte) 0x34;
                header[2] = (byte) (0 & 0xFF);
                header[3] = (byte) (2 & 0xFF);
                header[4] = (byte) (0 & 0xFF);
                header[5] = (byte) (0 & 0xFF);
                ByteBuffer buffer = ByteBuffer.wrap(header);
                headerDataBuffer = buffer;
            }
        }
        if (headerDataBuffer != null) {

            ByteBuffer buffer = headerDataBuffer;
            int r;
            do {
                r = next.write(buffer);
                if (r == 0) {
                    return false;
                }
            } while (buffer.hasRemaining());
            headerDataBuffer = null;
            return true;
        }
        return true;
    }

    private boolean notNull(Boolean attachment) {
        return attachment == null ? false : attachment;
    }

    private int notNull(Integer attachment) {
        return attachment == null ? 0 : attachment;
    }

    private String notNull(String attachment) {
        return attachment == null ? "" : attachment;
    }

    private boolean writeCurrentBuffer() throws IOException {
        ByteBuffer buffer = currentDataBuffer.getResource();
        int r;
        do {
            r = next.write(buffer);
            if (r == 0) {
                return false;
            }
        } while (buffer.hasRemaining());
        currentDataBuffer.free();
        currentDataBuffer = null;
        return true;
    }


    public int write(final ByteBuffer src) throws IOException {
        if(anyAreSet(state, FLAG_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (!processWrite()) {
            return 0;
        }
        if (src.remaining() == 0) {
            return 0;
        }
        long remaining = state & STATE_MASK;

        if (remaining == 0 && requestedChunkSize <= 0) {
            next.suspendWrites();
            return 0;
        }
        if (remaining == 0) {
            headerDataBuffer = createHeader(src);
            requestedChunkSize = 0;
            remaining = state & STATE_MASK; //this is a bit yuck
        }
        int limit = src.limit();
        if (src.remaining() > remaining) {
            src.limit((int) (src.position() + remaining));
        }
        try {
            ByteBuffer[] bufs;
            int headerLength = 0;
            if (src.remaining() == remaining) {
                if (headerDataBuffer == null) {
                    bufs = new ByteBuffer[]{src};
                } else {
                    bufs = new ByteBuffer[]{headerDataBuffer, src};
                    headerLength = headerDataBuffer.remaining();
                }
            } else {
                if (headerDataBuffer == null) {
                    bufs = new ByteBuffer[]{src};
                } else {
                    bufs = new ByteBuffer[]{headerDataBuffer, src};
                    headerLength = headerDataBuffer.remaining();
                }
            }
            int r = (int) next.write(bufs, 0, bufs.length);
            r -= headerLength;
            if(!headerDataBuffer.hasRemaining()) {
                headerDataBuffer = null;
            }
            if (r > 0) {
                remaining -= r;
                if (remaining < 0) {
                    remaining = 0;
                    r -= 1;
                }
                if(totalRemaining > 0) {
                    totalRemaining -= r;
                }
                return r;
            } else {
                return 0;
            }
        } finally {
            src.limit(limit);
            this.state = (state & ~STATE_MASK) | remaining;
        }
    }

    private ByteBuffer createHeader(final ByteBuffer src) {
        int remaining = src.remaining();
        remaining = Math.min(remaining, MAX_DATA_SIZE);
        remaining = Math.min(remaining, requestedChunkSize);
        int bodySize = remaining + 3;
        byte[] header = new byte[6];
        header[0] = (byte) 0x12;
        header[1] = (byte) 0x34;
        header[2] = (byte) ((bodySize >> 8) & 0xFF);
        header[3] = (byte) (bodySize & 0xFF);
        header[4] = (byte) ((remaining >> 8) & 0xFF);
        header[5] = (byte) (remaining & 0xFF);
        this.state = (state & ~STATE_MASK) | remaining;
        return ByteBuffer.wrap(header);
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

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
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
        if (allAreClear(state, FLAG_SHUTDOWN)) {
            return next.flush();
        }
        if (!handleFinalChunk()) {
            return false;
        }
        long state = this.state;
        if (allAreSet(state, FLAG_SHUTDOWN) && allAreClear(state, FLAG_DELEGATE_SHUTDOWN)) {
            if (finishListener != null) {
                finishListener.handleEvent(this);
            }
            this.state |= FLAG_DELEGATE_SHUTDOWN;
        }
        return next.flush();
    }

    public void suspendWrites() {
        state &= ~FLAG_WRITES_RESUMED;
        next.suspendWrites();
    }

    public void resumeWrites() {
        state |= FLAG_WRITES_RESUMED;
        long remaining = state & STATE_MASK;
        if (remaining != 0 || requestedChunkSize != 0) {
            next.resumeWrites();
        }
    }

    public boolean isWriteResumed() {
        return anyAreSet(state, FLAG_WRITES_RESUMED);
    }

    public void wakeupWrites() {
        state |= FLAG_WRITES_RESUMED;
        next.wakeupWrites();
    }

    public void terminateWrites() throws IOException {
        long remaining = state & STATE_MASK;
        if (remaining != 0) {
            try {
                throw UndertowClientMessages.MESSAGES.dataStillRemainingInChunk(remaining);
            } finally {
                next.truncateWrites();
            }
        }
        if (totalRemaining > 0) {
            try {
                throw new FixedLengthUnderflowException(totalRemaining + " bytes remaining");
            } finally {
                next.truncateWrites();
            }
        }
        long state = this.state;
        if (anyAreSet(state, FLAG_SHUTDOWN)) {
            return;
        }
        this.state |= FLAG_SHUTDOWN;
    }

    public void awaitWritable() throws IOException {
        throw new IllegalStateException();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        throw new IllegalStateException();
    }
}
