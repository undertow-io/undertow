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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.zip.Deflater;

import io.undertow.server.Connectors;
import org.xnio.Buffers;
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
import io.undertow.util.NewInstanceObjectPool;
import io.undertow.util.ObjectPool;
import io.undertow.util.Headers;
import io.undertow.util.PooledObject;
import io.undertow.util.SimpleObjectPool;

/**
 * Channel that handles deflate compression
 *
 * @author Stuart Douglas
 */
public class DeflatingStreamSinkConduit implements StreamSinkConduit {
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    protected volatile Deflater deflater;

    protected final PooledObject<Deflater> pooledObject;
    private final ConduitFactory<StreamSinkConduit> conduitFactory;
    private final HttpServerExchange exchange;

    private StreamSinkConduit next;
    private WriteReadyHandler writeReadyHandler;


    /**
     * The streams buffer. This is freed when the next is shutdown
     */
    protected PooledByteBuffer currentBuffer;
    /**
     * Buffer used to write the trailer if currentBuffer doesn't have enough space.
     */
    private ByteBuffer trailerBuffer;

    @SuppressWarnings("unused")
    private volatile int state = 0;
    private static final AtomicIntegerFieldUpdater<DeflatingStreamSinkConduit> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(DeflatingStreamSinkConduit.class, "state");

    private static final int SHUTDOWN = 1;
    private static final int NEXT_SHUTDOWN = 1 << 1;
    private static final int FLUSHING_BUFFER = 1 << 2;
    private static final int WRITES_RESUMED = 1 << 3;
    private static final int CLOSED = 1 << 4;
    private static final int WRITTEN_TRAILER = 1 << 5;

    public DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange) {
        this(conduitFactory, exchange, Deflater.DEFLATED);
    }

    public DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange, int deflateLevel) {
        this(conduitFactory, exchange, newInstanceDeflaterPool(deflateLevel));
    }

    public DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange, ObjectPool<Deflater> deflaterPool) {
        this.pooledObject = deflaterPool.allocate();
        this.deflater = pooledObject.getObject();
        this.currentBuffer = exchange.getConnection().getByteBufferPool().allocate();
        this.exchange = exchange;
        this.conduitFactory = conduitFactory;
        setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler<>(Connectors.getConduitSinkChannel(exchange)));
    }

    public static ObjectPool<Deflater> newInstanceDeflaterPool(int deflateLevel) {
        return new NewInstanceObjectPool<>(() -> new Deflater(deflateLevel, true), Deflater::end);
    }

    public static ObjectPool<Deflater> simpleDeflaterPool(int poolSize, int deflateLevel) {
        return new SimpleObjectPool<>(poolSize, () -> new Deflater(deflateLevel, true), Deflater::reset, Deflater::end);
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
            if (!src.hasRemaining()) {
                return 0;
            }
            int initialSrcPosition = src.position();
            int initialRemaining = src.remaining();
            deflater.setInput(src);
            deflateData(false);
            int consumed = initialRemaining - src.remaining();
            Connectors.updateResponseBytesSent(exchange, -consumed);
            int endSrcPosition = src.position();
            int srcLimit = src.limit();
            // Reset the buffer to original values with a limit based on what has
            // been deflated such that only data that has been compressed is
            // represented by the buffer.
            src.position(initialSrcPosition);
            src.limit(endSrcPosition);
            postDeflate(src);
            src.limit(srcLimit);
            src.position(endSrcPosition);
            // Ensure input buffers are not held or clobbered outside expected usage
            deflater.setInput(EMPTY);
            return consumed;
        } catch (IOException | RuntimeException | Error e) {
            freeBuffer();
            throw e;
        }
    }

    protected void postDeflate(ByteBuffer data) {
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (anyAreSet(state, SHUTDOWN | CLOSED) || currentBuffer == null) {
            throw new ClosedChannelException();
        }
        try {
            int total = 0;
            for (int i = offset; i < offset + length; ++i) {
                ByteBuffer buf = srcs[i];
                if (buf.hasRemaining()) {
                    int ret = write(buf);
                    total += ret;
                    // Must cease iteration after any buffer is not
                    // fully exhausted, or after bytes cannot be
                    // written.
                    if (ret == 0 || buf.hasRemaining()) {
                        return total;
                    }
                }
            }
            return total;
        } catch (IOException | RuntimeException | Error e) {
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
            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, ~WRITES_RESUMED, (currentState, flag)-> currentState & flag);
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
            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, WRITES_RESUMED, (currentState, flag)-> currentState | flag);
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
        if (deflater != null) {
            deflater.finish();
        }
        stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, SHUTDOWN, (currentState, flag)-> currentState | flag);
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
                            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, WRITTEN_TRAILER, (currentState, flag)-> currentState | flag);
                            byte[] data = getTrailer();
                            if (data != null) {
                                Connectors.updateResponseBytesSent(exchange, data.length);
                                if(trailerBuffer != null) {
                                    throw new IllegalStateException("trailerBuffer is already set");
                                } else if(anyAreSet(state, FLUSHING_BUFFER) && buffer.capacity() - buffer.remaining() >= data.length) {
                                    buffer.compact();
                                    buffer.put(data);
                                    buffer.flip();
                                } else if (data.length <= buffer.remaining() && !anyAreSet(state, FLUSHING_BUFFER)) {
                                    buffer.put(data);
                                } else {
                                    trailerBuffer = ByteBuffer.wrap(data);
                                }
                            }
                        }

                        //ok the deflater is flushed, now we need to flush the buffer
                        if (!anyAreSet(state, FLUSHING_BUFFER)) {
                            buffer.flip();
                            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, FLUSHING_BUFFER, (currentState, flag)-> currentState | flag);
                            if (next == null) {
                                nextCreated = true;
                                this.next = createNextChannel();
                            }
                        }
                        if (performFlushIfRequired()) {
                            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, NEXT_SHUTDOWN, (currentState, flag)-> currentState | flag);
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
                            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, FLUSHING_BUFFER, (currentState, flag)-> currentState | flag);
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
                        } catch (Throwable e) {
                            UndertowLogger.REQUEST_LOGGER.debug("Failed to resume", e);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException | Error e) {
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
            return trailerBuffer == null
                    ? performFlushIfRequiredSingleBuffer()
                    : performFlushIfRequiredAdditionalBuffer();
        }
        return true;
    }

    private boolean performFlushIfRequiredSingleBuffer() throws IOException {
        final ByteBuffer buf =  currentBuffer.getBuffer();
        long totalLength = buf.remaining();
        if (totalLength > 0) {
            int total = 0;
            int res = 0;
            do {
                res = next.write(buf);
                total += res;
                if (res == 0) {
                    return false;
                }
            } while (total < totalLength);
        }
        currentBuffer.getBuffer().clear();
        stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, ~FLUSHING_BUFFER, (currentState, flag)-> currentState & flag);
        return true;
    }

    private boolean performFlushIfRequiredAdditionalBuffer() throws IOException {
        final ByteBuffer[] bufs = new ByteBuffer[] {
                currentBuffer.getBuffer(),
                trailerBuffer};
        long totalLength = Buffers.remaining(bufs);
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
        trailerBuffer = null;
        currentBuffer.getBuffer().clear();
        stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, ~FLUSHING_BUFFER, (currentState, flag)-> currentState & flag);
        return true;
    }

    private StreamSinkConduit createNextChannel() {
        if (deflater.finished() && allAreSet(state, WRITTEN_TRAILER)) {
            //the deflater was fully flushed before we created the channel. This means that what is in the buffer is
            //all there is
            int remaining = currentBuffer.getBuffer().remaining();
            if (trailerBuffer != null) {
                remaining += trailerBuffer.remaining();
            }
            if(!exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Integer.toString(remaining));
            }
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
        try {
            PooledByteBuffer pooled = this.currentBuffer;
            final ByteBuffer outputBuffer = pooled.getBuffer();

            final boolean shutdown = anyAreSet(state, SHUTDOWN);
            while (force || !deflater.needsInput() || (shutdown && !deflater.finished())) {
                int count = deflater.deflate(outputBuffer, force ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
                if (count != 0) {
                    Connectors.updateResponseBytesSent(exchange, count);
                    if (!outputBuffer.hasRemaining()) {
                        outputBuffer.flip();
                        stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, FLUSHING_BUFFER, (currentState, flag)-> currentState | flag);
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
        stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, CLOSED, (currentState, flag)-> currentState | flag);
        next.truncateWrites();
    }

    private void freeBuffer() {
        if (currentBuffer != null) {
            currentBuffer.close();
            currentBuffer = null;
            stateUpdater.getAndAccumulate(DeflatingStreamSinkConduit.this, ~FLUSHING_BUFFER, (currentState, flag)-> currentState & flag);
        }
        if (deflater != null) {
            deflater = null;
            pooledObject.close();
        }
        trailerBuffer = null;
    }
}
