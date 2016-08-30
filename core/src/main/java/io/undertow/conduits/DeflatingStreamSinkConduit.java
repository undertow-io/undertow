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

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import io.undertow.server.Connectors;
import org.xnio.IoUtils;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;

/**
 * Channel that handles deflate compression
 *
 * @author Stuart Douglas
 */
public class DeflatingStreamSinkConduit implements StreamSinkConduit {

    protected final Deflater deflater;
    private final ConduitFactory<StreamSinkConduit> conduitFactory;
    private final HttpServerExchange exchange;

    private StreamSinkConduit next;
    private WriteReadyHandler writeReadyHandler;


    /**
     * The streams buffer. This is freed when the next is shutdown
     */
    protected PooledByteBuffer currentBuffer;
    /**
     * there may have been some additional data that did not fit into the first buffer
     */
    private ByteBuffer additionalBuffer;

    private int state = 0;

    private static final int SHUTDOWN = 1;
    private static final int NEXT_SHUTDOWN = 1 << 1;
    private static final int FLUSHING_BUFFER = 1 << 2;
    private static final int WRITES_RESUMED = 1 << 3;
    private static final int CLOSED = 1 << 4;
    private static final int WRITTEN_TRAILER = 1 << 5;

    public DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange) {
        this(conduitFactory, exchange, Deflater.DEFLATED);
    }

    protected DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange, int deflateLevel) {
        deflater = new Deflater(deflateLevel, true);
        this.currentBuffer = exchange.getConnection().getByteBufferPool().allocate();
        this.exchange = exchange;
        this.conduitFactory = conduitFactory;
        setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler<>(Connectors.getConduitSinkChannel(exchange)));
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, SHUTDOWN | CLOSED) || currentBuffer == null) {
            throw new ClosedChannelException();
        }
        try {
            if (!performFlushIfRequired()) {
                return 0;
            }
            if (src.remaining() == 0) {
                return 0;
            }
            //we may already have some input, if so compress it
            if (!deflater.needsInput()) {
                deflateData(false);
                if (!deflater.needsInput()) {
                    return 0;
                }
            }
            byte[] data = new byte[src.remaining()];
            src.get(data);
            preDeflate(data);
            deflater.setInput(data);
            Connectors.updateResponseBytesSent(exchange, 0 - data.length);
            deflateData(false);
            return data.length;
        } catch (IOException e) {
            freeBuffer();
            throw e;
        }
    }

    protected void preDeflate(byte[] data) {

    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (anyAreSet(state, SHUTDOWN | CLOSED) || currentBuffer == null) {
            throw new ClosedChannelException();
        }
        try {
            int total = 0;
            for (int i = offset; i < offset + length; ++i) {
                if (srcs[i].hasRemaining()) {
                    int ret = write(srcs[i]);
                    total += ret;
                    if (ret == 0) {
                        return total;
                    }
                }
            }
            return total;
        } catch (IOException e) {
            freeBuffer();
            throw e;
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(state, SHUTDOWN | CLOSED)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }


    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, SHUTDOWN | CLOSED)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public XnioWorker getWorker() {
        return exchange.getConnection().getWorker();
    }

    @Override
    public void suspendWrites() {
        if (next == null) {
            state = state & ~WRITES_RESUMED;
        } else {
            next.suspendWrites();
        }
    }


    @Override
    public boolean isWriteResumed() {
        if (next == null) {
            return anyAreSet(state, WRITES_RESUMED);
        } else {
            return next.isWriteResumed();
        }
    }

    @Override
    public void wakeupWrites() {
        if (next == null) {
            resumeWrites();
        } else {
            next.wakeupWrites();
        }
    }

    @Override
    public void resumeWrites() {
        if (next == null) {
            state |= WRITES_RESUMED;
            queueWriteListener();
        } else {
            next.resumeWrites();
        }
    }

    private void queueWriteListener() {
        exchange.getConnection().getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                if (writeReadyHandler != null) {
                    try {
                        writeReadyHandler.writeReady();
                    } finally {
                        //if writes are still resumed queue up another one
                        if (next == null && isWriteResumed()) {
                            queueWriteListener();
                        }
                    }
                }
            }
        });
    }

    @Override
    public void terminateWrites() throws IOException {
        deflater.finish();
        state |= SHUTDOWN;
    }

    @Override
    public boolean isWriteShutdown() {
        return anyAreSet(state, SHUTDOWN);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (next == null) {
            return;
        } else {
            next.awaitWritable();
        }
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (next == null) {
            return;
        } else {
            next.awaitWritable(time, timeUnit);
        }
    }

    @Override
    public XnioIoThread getWriteThread() {
        return exchange.getConnection().getIoThread();
    }

    @Override
    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        this.writeReadyHandler = handler;
    }

    @Override
    public boolean flush() throws IOException {
        if (currentBuffer == null) {
            if (anyAreSet(state, NEXT_SHUTDOWN)) {
                return next.flush();
            } else {
                return true;
            }
        }
        try {
            boolean nextCreated = false;
            try {
                if (anyAreSet(state, SHUTDOWN)) {
                    if (anyAreSet(state, NEXT_SHUTDOWN)) {
                        return next.flush();
                    } else {
                        if (!performFlushIfRequired()) {
                            return false;
                        }
                        //if the deflater has not been fully flushed we need to flush it
                        if (!deflater.finished()) {
                            deflateData(false);
                            //if could not fully flush
                            if (!deflater.finished()) {
                                return false;
                            }
                        }
                        final ByteBuffer buffer = currentBuffer.getBuffer();
                        if (allAreClear(state, WRITTEN_TRAILER)) {
                            state |= WRITTEN_TRAILER;
                            byte[] data = getTrailer();
                            if (data != null) {
                                Connectors.updateResponseBytesSent(exchange, data.length);
                                if(additionalBuffer != null) {
                                    byte[] newData = new byte[additionalBuffer.remaining() + data.length];
                                    int pos = 0;
                                    while (additionalBuffer.hasRemaining()) {
                                        newData[pos++] = additionalBuffer.get();
                                    }
                                    for (byte aData : data) {
                                        newData[pos++] = aData;
                                    }
                                    this.additionalBuffer = ByteBuffer.wrap(newData);
                                } else if(anyAreSet(state, FLUSHING_BUFFER) && buffer.capacity() - buffer.remaining() >= data.length) {
                                    buffer.compact();
                                    buffer.put(data);
                                    buffer.flip();
                                } else if (data.length <= buffer.remaining() && !anyAreSet(state, FLUSHING_BUFFER)) {
                                    buffer.put(data);
                                } else {
                                    additionalBuffer = ByteBuffer.wrap(data);
                                }
                            }
                        }

                        //ok the deflater is flushed, now we need to flush the buffer
                        if (!anyAreSet(state, FLUSHING_BUFFER)) {
                            buffer.flip();
                            state |= FLUSHING_BUFFER;
                            if (next == null) {
                                nextCreated = true;
                                this.next = createNextChannel();
                            }
                        }
                        if (performFlushIfRequired()) {
                            state |= NEXT_SHUTDOWN;
                            freeBuffer();
                            next.terminateWrites();
                            return next.flush();
                        } else {
                            return false;
                        }
                    }
                } else {
                    if(allAreClear(state, FLUSHING_BUFFER)) {
                        if (next == null) {
                            nextCreated = true;
                            this.next = createNextChannel();
                        }
                        deflateData(true);
                        if(allAreClear(state, FLUSHING_BUFFER)) {
                            //deflateData can cause this to be change
                            currentBuffer.getBuffer().flip();
                            this.state |= FLUSHING_BUFFER;
                        }
                    }
                    if(!performFlushIfRequired()) {
                        return false;
                    }
                    return next.flush();
                }
            } finally {
                if (nextCreated) {
                    if (anyAreSet(state, WRITES_RESUMED) && !anyAreSet(state ,NEXT_SHUTDOWN)) {
                        try {
                            next.resumeWrites();
                        } catch (Exception e) {
                            UndertowLogger.REQUEST_LOGGER.debug("Failed to resume", e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            freeBuffer();
            throw e;
        }
    }

    /**
     * called before the stream is finally flushed.
     */
    protected byte[] getTrailer() {
        return null;
    }

    /**
     * The we are in the flushing state then we flush to the underlying stream, otherwise just return true
     *
     * @return false if there is still more to flush
     */
    private boolean performFlushIfRequired() throws IOException {
        if (anyAreSet(state, FLUSHING_BUFFER)) {
            final ByteBuffer[] bufs = new ByteBuffer[additionalBuffer == null ? 1 : 2];
            long totalLength = 0;
            bufs[0] = currentBuffer.getBuffer();
            totalLength += bufs[0].remaining();
            if (additionalBuffer != null) {
                bufs[1] = additionalBuffer;
                totalLength += bufs[1].remaining();
            }
            if (totalLength > 0) {
                long total = 0;
                long res = 0;
                do {
                    res = next.write(bufs, 0, bufs.length);
                    total += res;
                    if (res == 0) {
                        return false;
                    }
                } while (total < totalLength);
            }
            additionalBuffer = null;
            currentBuffer.getBuffer().clear();
            state = state & ~FLUSHING_BUFFER;
        }
        return true;
    }


    private StreamSinkConduit createNextChannel() {
        if (deflater.finished() && allAreSet(state, WRITTEN_TRAILER)) {
            //the deflater was fully flushed before we created the channel. This means that what is in the buffer is
            //all there is
            int remaining = currentBuffer.getBuffer().remaining();
            if (additionalBuffer != null) {
                remaining += additionalBuffer.remaining();
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Integer.toString(remaining));
        } else {
            exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
        }
        return conduitFactory.create();
    }

    /**
     * Runs the current data through the deflater. As much as possible this will be buffered in the current output
     * stream.
     *
     * @throws IOException
     */
    private void deflateData(boolean force) throws IOException {
        //we don't need to flush here, as this should have been called already by the time we get to
        //this point
        boolean nextCreated = false;
        try (PooledByteBuffer arrayPooled = this.exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate()) {
            PooledByteBuffer pooled = this.currentBuffer;
            final ByteBuffer outputBuffer = pooled.getBuffer();

            final boolean shutdown = anyAreSet(state, SHUTDOWN);
            ByteBuffer buf = arrayPooled.getBuffer();
            while (force || !deflater.needsInput() || (shutdown && !deflater.finished())) {
                int count = deflater.deflate(buf.array(), buf.arrayOffset(), buf.remaining(), force ? Deflater.SYNC_FLUSH: Deflater.NO_FLUSH);
                Connectors.updateResponseBytesSent(exchange, count);
                if (count != 0) {
                    int remaining = outputBuffer.remaining();
                    if (remaining > count) {
                        outputBuffer.put(buf.array(), buf.arrayOffset(), count);
                    } else {
                        if (remaining == count) {
                            outputBuffer.put(buf.array(), buf.arrayOffset(), count);
                        } else {
                            outputBuffer.put(buf.array(), buf.arrayOffset(), remaining);
                            additionalBuffer = ByteBuffer.allocate(count - remaining);
                            additionalBuffer.put(buf.array(), buf.arrayOffset() + remaining, count - remaining);
                            additionalBuffer.flip();
                        }
                        outputBuffer.flip();
                        this.state |= FLUSHING_BUFFER;
                        if (next == null) {
                            nextCreated = true;
                            this.next = createNextChannel();
                        }
                        if (!performFlushIfRequired()) {
                            return;
                        }
                    }
                } else {
                    force = false;
                }
            }
        } finally {
            if (nextCreated) {
                if (anyAreSet(state, WRITES_RESUMED)) {
                    next.resumeWrites();
                }
            }
        }
    }


    @Override
    public void truncateWrites() throws IOException {
        freeBuffer();
        state |= CLOSED;
        next.truncateWrites();
    }

    private void freeBuffer() {
        if (currentBuffer != null) {
            currentBuffer.close();
            currentBuffer = null;
            state = state & ~FLUSHING_BUFFER;
        }
    }
}
