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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.undertow.UndertowLogger;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.ImmediatePooledByteBuffer;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Channel that implements HTTP chunked transfer coding.
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    /**
     * Trailers that are to be attached to the end of the HTTP response. Note that it is the callers responsibility
     * to make sure the client understands trailers (i.e. they have provided a TE header), and to set the 'Trailers:'
     * header appropriately.
     * <p>
     * This attachment must be set before the {@link #terminateWrites()} method is called.
     */
    @Deprecated
    public static final AttachmentKey<HeaderMap> TRAILERS = HttpAttachments.RESPONSE_TRAILERS;

    private final HeaderMap responseHeaders;

    private final ConduitListener<? super ChunkedStreamSinkConduit> finishListener;
    private final int config;

    private final ByteBufferPool bufferPool;

    /**
     * "0\r\n" as bytes in US ASCII encoding.
     */
    private static final byte[] LAST_CHUNK = new byte[] {(byte) 48, (byte) 13, (byte) 10};

    /**
     * "\r\n" as bytes in US ASCII encoding.
     */
    private static final byte[] CRLF = new byte[] {(byte) 13, (byte) 10};

    private final Attachable attachable;
    private int state;
    private int chunkleft = 0;

    private final ByteBuffer chunkingBuffer = ByteBuffer.allocate(12); //12 is the most
    private final ByteBuffer chunkingSepBuffer;
    private PooledByteBuffer lastChunkBuffer;


    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    /**
     * Flag that is set when {@link #terminateWrites()} or @{link #close()} is called
     */
    private static final int FLAG_WRITES_SHUTDOWN = 1;
    private static final int FLAG_NEXT_SHUTDOWN = 1 << 2;
    private static final int FLAG_WRITTEN_FIRST_CHUNK = 1 << 3;
    private static final int FLAG_FIRST_DATA_WRITTEN = 1 << 4; //set on first flush or write call
    private static final int FLAG_FINISHED = 1 << 5;

    /**
     * Construct a new instance.
     *
     * @param next            the channel to wrap
     * @param configurable    {@code true} to allow configuration of the next channel, {@code false} otherwise
     * @param passClose       {@code true} to close the underlying channel when this channel is closed, {@code false} otherwise
     * @param responseHeaders The response headers
     * @param finishListener  The finish listener
     * @param attachable      The attachable
     */
    public ChunkedStreamSinkConduit(final StreamSinkConduit next, final ByteBufferPool bufferPool, final boolean configurable, final boolean passClose, HeaderMap responseHeaders, final ConduitListener<? super ChunkedStreamSinkConduit> finishListener, final Attachable attachable) {
        super(next);
        this.bufferPool = bufferPool;
        this.responseHeaders = responseHeaders;
        this.finishListener = finishListener;
        this.attachable = attachable;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (passClose ? CONF_FLAG_PASS_CLOSE : 0);
        chunkingSepBuffer = ByteBuffer.allocate(2);
        chunkingSepBuffer.flip();
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return doWrite(src);
    }


    int doWrite(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if(src.remaining() == 0) {
            return 0;
        }
        this.state |= FLAG_FIRST_DATA_WRITTEN;
        int oldLimit = src.limit();
        boolean dataRemaining = false; //set to true if there is data in src that still needs to be written out
        if (chunkleft == 0 && !chunkingSepBuffer.hasRemaining()) {
            chunkingBuffer.clear();
            putIntAsHexString(chunkingBuffer, src.remaining());
            chunkingBuffer.put(CRLF);
            chunkingBuffer.flip();
            chunkingSepBuffer.clear();
            chunkingSepBuffer.put(CRLF);
            chunkingSepBuffer.flip();
            state |= FLAG_WRITTEN_FIRST_CHUNK;
            chunkleft = src.remaining();
        } else {
            if (src.remaining() > chunkleft) {
                dataRemaining = true;
                src.limit(chunkleft + src.position());
            }
        }
        try {
            int chunkingSize = chunkingBuffer.remaining();
            int chunkingSepSize = chunkingSepBuffer.remaining();
            if (chunkingSize > 0 || chunkingSepSize > 0 || lastChunkBuffer != null) {
                int originalRemaining = src.remaining();
                long result;
                if (lastChunkBuffer == null || dataRemaining) {
                    final ByteBuffer[] buf = new ByteBuffer[]{chunkingBuffer, src, chunkingSepBuffer};
                    result = next.write(buf, 0, buf.length);
                } else {
                    final ByteBuffer[] buf = new ByteBuffer[]{chunkingBuffer, src, lastChunkBuffer.getBuffer()};
                    if (anyAreSet(state, CONF_FLAG_PASS_CLOSE)) {
                        result = next.writeFinal(buf, 0, buf.length);
                    } else {
                        result = next.write(buf, 0, buf.length);
                    }
                    if (!src.hasRemaining()) {
                        state |= FLAG_WRITES_SHUTDOWN;
                    }
                    if (!lastChunkBuffer.getBuffer().hasRemaining()) {
                        state |= FLAG_NEXT_SHUTDOWN;
                        lastChunkBuffer.close();
                    }
                }
                int srcWritten = originalRemaining - src.remaining();
                chunkleft -= srcWritten;
                if (result < chunkingSize) {
                    return 0;
                } else {
                    return srcWritten;
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

    @Override
    public void truncateWrites() throws IOException {
        try {
            if (lastChunkBuffer != null) {
                lastChunkBuffer.close();
            }
            if (allAreClear(state, FLAG_FINISHED)) {
                invokeFinishListener();
            }
        } finally {
            super.truncateWrites();
        }
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        for (int i = 0; i < length; i++) {
            ByteBuffer srcBuffer = srcs[offset + i];
            if (srcBuffer.hasRemaining()) {
                return write(srcBuffer);
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
        //todo: we could optimise this to just set a content length if no data has been written
        if(!src.hasRemaining()) {
            terminateWrites();
            return 0;
        }
        if (lastChunkBuffer == null) {
            createLastChunk(true);
        }
        return doWrite(src);
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
        this.state |= FLAG_FIRST_DATA_WRITTEN;
        if (anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            if (anyAreSet(state, FLAG_NEXT_SHUTDOWN)) {
                boolean val = next.flush();
                if (val && allAreClear(state, FLAG_FINISHED)) {
                    invokeFinishListener();
                }
                return val;
            } else {
                next.write(lastChunkBuffer.getBuffer());
                if (!lastChunkBuffer.getBuffer().hasRemaining()) {
                    lastChunkBuffer.close();
                    if (anyAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                        next.terminateWrites();
                    }
                    state |= FLAG_NEXT_SHUTDOWN;
                    boolean val = next.flush();
                    if (val && allAreClear(state, FLAG_FINISHED)) {
                        invokeFinishListener();
                    }
                    return val;
                } else {
                    return false;
                }
            }
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
        if(anyAreSet(state, FLAG_WRITES_SHUTDOWN)) {
            return;
        }
        if (this.chunkleft != 0) {
            UndertowLogger.REQUEST_IO_LOGGER.debugf("Channel closed mid-chunk");
            next.truncateWrites();
        }
        if (!anyAreSet(state, FLAG_FIRST_DATA_WRITTEN)) {
            //if no data was actually sent we just remove the transfer encoding header, and set content length 0
            //TODO: is this the best way to do it?
            //todo: should we make this behaviour configurable?
            responseHeaders.put(Headers.CONTENT_LENGTH, "0"); //according to the spec we don't actually need this, but better to be safe
            responseHeaders.remove(Headers.TRANSFER_ENCODING);
            state |= FLAG_NEXT_SHUTDOWN | FLAG_WRITES_SHUTDOWN;
            if(anyAreSet(state, CONF_FLAG_PASS_CLOSE)) {
                next.terminateWrites();
            }
        } else {
            createLastChunk(false);
            state |= FLAG_WRITES_SHUTDOWN;
        }
    }

    private void createLastChunk(final boolean writeFinal) throws UnsupportedEncodingException {
        PooledByteBuffer lastChunkBufferPooled =  bufferPool.allocate();
        ByteBuffer lastChunkBuffer = lastChunkBufferPooled.getBuffer();
        if (writeFinal) {
            lastChunkBuffer.put(CRLF);
        } else if(chunkingSepBuffer.hasRemaining()) {
            //the end of chunk /r/n has not been written yet
            //just add it to this buffer to make managing state easier
            lastChunkBuffer.put(chunkingSepBuffer);
        }
        lastChunkBuffer.put(LAST_CHUNK);
        //we just assume it will fit
        HeaderMap attachment = attachable.getAttachment(HttpAttachments.RESPONSE_TRAILERS);
        final HeaderMap trailers;
        Supplier<HeaderMap> supplier = attachable.getAttachment(HttpAttachments.RESPONSE_TRAILER_SUPPLIER);
        if(attachment != null && supplier == null) {
            trailers = attachment;
        } else if(attachment == null && supplier != null) {
            trailers = supplier.get();
        } else if(attachment != null) {
            HeaderMap supplied = supplier.get();
            for(HeaderValues k : supplied) {
                attachment.putAll(k.getHeaderName(), k);
            }
            trailers = attachment;
        } else {
            trailers = null;
        }
        if (trailers != null && trailers.size() != 0) {
            for (HeaderValues trailer : trailers) {
                for (String val : trailer) {
                    trailer.getHeaderName().appendTo(lastChunkBuffer);
                    lastChunkBuffer.put((byte) ':');
                    lastChunkBuffer.put((byte) ' ');
                    lastChunkBuffer.put(val.getBytes(StandardCharsets.US_ASCII));
                    lastChunkBuffer.put(CRLF);
                }
            }
            lastChunkBuffer.put(CRLF);
        } else {
            lastChunkBuffer.put(CRLF);
        }
        //horrible hack
        //there is a situation where we can get a buffer leak here if the connection is terminated abnormaly
        //this should be fixed once this channel has its lifecycle tied to the connection, same as fixed length
        lastChunkBuffer.flip();
        ByteBuffer data = ByteBuffer.allocate(lastChunkBuffer.remaining());
        data.put(lastChunkBuffer);
        data.flip();
        this.lastChunkBuffer = new ImmediatePooledByteBuffer(data);

        lastChunkBufferPooled.close();
    }

    @Override
    public void awaitWritable() throws IOException {
        next.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        next.awaitWritable(time, timeUnit);
    }

    private static void putIntAsHexString(final ByteBuffer buf, final int v) {
        byte int3 = (byte) (v >> 24);
        byte int2 = (byte) (v >> 16);
        byte int1 = (byte) (v >>  8);
        byte int0 = (byte) (v      );
        boolean nonZeroFound = false;
        if (int3 != 0) {
            buf.put(DIGITS[(0xF0 & int3) >>> 4])
               .put(DIGITS[0x0F & int3]);
            nonZeroFound = true;
        }
        if (nonZeroFound || int2 != 0) {
            buf.put(DIGITS[(0xF0 & int2) >>> 4])
               .put(DIGITS[0x0F & int2]);
            nonZeroFound = true;
        }
        if (nonZeroFound || int1 != 0) {
            buf.put(DIGITS[(0xF0 & int1) >>> 4])
               .put(DIGITS[0x0F & int1]);
        }
        buf.put(DIGITS[(0xF0 & int0) >>> 4])
           .put(DIGITS[0x0F & int0]);
    }

    /**
     * hexadecimal digits "0123456789abcdef" as bytes in US ASCII encoding.
     */
    private static final byte[] DIGITS = new byte[] {
        (byte) 48, (byte) 49, (byte) 50, (byte) 51, (byte) 52, (byte) 53,
        (byte) 54, (byte) 55, (byte) 56, (byte) 57, (byte) 97, (byte) 98,
        (byte) 99, (byte) 100, (byte) 101, (byte) 102};

}
