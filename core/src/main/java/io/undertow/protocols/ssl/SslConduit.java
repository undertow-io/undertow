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

package io.undertow.protocols.ssl;

import io.undertow.UndertowLogger;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * @author Stuart Douglas
 */
public class SslConduit implements StreamSourceConduit, StreamSinkConduit {

    /**
     * If this is set we are in the middle of a handshake, and we cannot
     * read any more data until we have written out our wrap result
     */
    private static final int FLAG_READ_REQUIRES_WRITE = 1;
    /**
     * If this is set we are in the process of handshaking and we cannot write any
     * more data until we have read unwrapped data from the remote peer
     */
    private static final int FLAG_WRITE_REQUIRES_READ = 1 << 1;
    /**
     * If reads are resumed. The underlying delegate may not be resumed if a write is required
     * to make progress.
     */
    private static final int FLAG_READS_RESUMED = 1 << 2;
    /**
     * If writes are resumed, the underlying delegate may not be resumed if a read is required
     */
    private static final int FLAG_WRITES_RESUMED = 1 << 3;

    /**
     * If there is data in the {@link #dataToUnwrap} buffer, and the last unwrap attempt did not result
     * in a buffer underflow
     */
    private static final int FLAG_DATA_TO_UNWRAP = 1 << 4;
    /**
     * If the user has shutdown reads
     */
    private static final int FLAG_READ_SHUTDOWN = 1 << 5;
    /**
     * If the user has shutdown writes
     */
    private static final int FLAG_WRITE_SHUTDOWN = 1 << 6;

    /**
     * If the engine has been shut down
     */
    private static final int FLAG_ENGINE_INBOUND_SHUTDOWN = 1 << 7;
    /**
     * If the engine has been shut down
     */
    private static final int FLAG_ENGINE_OUTBOUND_SHUTDOWN = 1 << 8;

    private static final int FLAG_DELEGATE_SINK_SHUTDOWN = 1 << 9;

    private static final int FLAG_DELEGATE_SOURCE_SHUTDOWN = 1 << 10;

    private static final int FLAG_IN_HANDSHAKE = 1 << 11;
    private static final int FLAG_CLOSED = 1 << 12;
    private static final int FLAG_WRITE_CLOSED = 1 << 13;
    private static final int FLAG_READ_CLOSED = 1 << 14;
    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);


    private final UndertowSslConnection connection;
    private final StreamConnection delegate;
    private final SSLEngine engine;
    private final StreamSinkConduit sink;
    private final StreamSourceConduit source;
    private final Pool<ByteBuffer> bufferPool;
    private final Runnable handshakeCallback;

    private int state = 0;

    private volatile int outstandingTasks = 0;

    /**
     * Data that has been wrapped and is ready to be sent to the underlying channel.
     *
     * This will be null if there is no data
     */
    private Pooled<ByteBuffer> wrappedData;
    /**
     * Data that has been read from the underlying channel, and needs to be unwrapped.
     *
     * This will be null if there is no data. If there is data the {@link #FLAG_DATA_TO_UNWRAP}
     * flag must still be checked, otherwise there may be situations where even though some data
     * has been read there is not enough to unwrap (i.e. the engine returned buffer underflow).
     */
    private Pooled<ByteBuffer> dataToUnwrap;

    /**
     * Unwrapped data, ready to be delivered to the application. Will be null if there is no data.
     *
     * If possible we avoid allocating this buffer, and instead unwrap directly into the end users buffer.
     */
    private Pooled<ByteBuffer> unwrappedData;

    private SslWriteReadyHandler writeReadyHandler;
    private SslReadReadyHandler readReadyHandler;


    SslConduit(UndertowSslConnection connection, StreamConnection delegate, SSLEngine engine, Pool<ByteBuffer> bufferPool, Runnable handshakeCallback) {
        this.connection = connection;
        this.delegate = delegate;
        this.handshakeCallback = handshakeCallback;
        this.sink = delegate.getSinkChannel().getConduit();
        this.source = delegate.getSourceChannel().getConduit();
        this.engine = engine;
        this.bufferPool = bufferPool;
        delegate.getSourceChannel().getConduit().setReadReadyHandler(readReadyHandler = new SslReadReadyHandler(null));
        delegate.getSinkChannel().getConduit().setWriteReadyHandler(writeReadyHandler = new SslWriteReadyHandler(null));
        if(engine.getUseClientMode()) {
            state = FLAG_IN_HANDSHAKE | FLAG_READ_REQUIRES_WRITE;
        } else {
            state = FLAG_IN_HANDSHAKE | FLAG_WRITE_REQUIRES_READ;
        }
    }

    @Override
    public void terminateReads() throws IOException {
        state |= FLAG_READ_SHUTDOWN;
        notifyReadClosed();
    }

    @Override
    public boolean isReadShutdown() {
        return anyAreSet(state, FLAG_READ_SHUTDOWN);
    }

    @Override
    public void resumeReads() {
        resumeReads(false);
    }
    @Override
    public void suspendReads() {
        state &= ~FLAG_READS_RESUMED;
        if(!allAreSet(state, FLAG_WRITES_RESUMED | FLAG_WRITE_REQUIRES_READ)) {
            delegate.getSourceChannel().suspendReads();
        }
    }

    @Override
    public void wakeupReads() {
        resumeReads(true);
    }

    private  void resumeReads(boolean wakeup) {
        state |= FLAG_READS_RESUMED;
        if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
            delegate.getSinkChannel().resumeWrites();
        } else {
            delegate.getSourceChannel().resumeReads();
            if(anyAreSet(state, FLAG_DATA_TO_UNWRAP) || wakeup) {
                runReadListener();
            }
        }
    }


    private void runReadListener() {
        try {
            delegate.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    readReadyHandler.readReady();
                }
            });
        } catch (Exception e) {
            //will only happen on shutdown
            IoUtils.safeClose(connection, delegate);
            UndertowLogger.REQUEST_IO_LOGGER.debugf(e, "Failed to queue read listener invocation");
        }
    }

    @Override
    public boolean isReadResumed() {
        return anyAreSet(state, FLAG_READS_RESUMED);
    }

    @Override
    public void awaitReadable() throws IOException {
        synchronized (this) {
            if(outstandingTasks > 0) {
                try {
                    wait();
                    return;
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
        }
        if(unwrappedData != null) {
            return;
        }
        if(anyAreSet(state, FLAG_DATA_TO_UNWRAP)) {
            return;
        }
        if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
            awaitWritable();
            return;
        }
        source.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        synchronized (this) {
            if(outstandingTasks > 0) {
                try {
                    wait(timeUnit.toMillis(time));
                    return;
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
        }
        if(unwrappedData != null) {
            return;
        }
        if(anyAreSet(state, FLAG_DATA_TO_UNWRAP)) {
            return;
        }
        if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
            awaitWritable(time, timeUnit);
            return;
        }
        source.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioIoThread getReadThread() {
        return delegate.getIoThread();
    }

    @Override
    public void setReadReadyHandler(ReadReadyHandler handler) {
        delegate.getSourceChannel().getConduit().setReadReadyHandler(readReadyHandler = new SslReadReadyHandler(handler));
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return (int) doWrap(new ByteBuffer[]{src}, 0, 1);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return doWrap(srcs, offs, len);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        state |= FLAG_WRITE_SHUTDOWN;
    }

    @Override
    public boolean isWriteShutdown() {
        return false; //todo
    }

    @Override
    public void resumeWrites() {
        state |= FLAG_WRITES_RESUMED;
        if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
            delegate.getSourceChannel().resumeReads();
        } else {
            delegate.getSinkChannel().resumeWrites();
        }
    }

    @Override
    public void suspendWrites() {
        state &= ~FLAG_WRITES_RESUMED;
        if(!allAreSet(state, FLAG_READS_RESUMED | FLAG_READ_REQUIRES_WRITE)) {
            delegate.getSinkChannel().suspendWrites();
        }
    }

    @Override
    public void wakeupWrites() {
        resumeWrites();
        getWriteThread().execute(new Runnable() {
            @Override
            public void run() {
                writeReadyHandler.writeReady();
            }
        });
    }

    @Override
    public boolean isWriteResumed() {
        return anyAreSet(state, FLAG_WRITES_RESUMED);
    }

    @Override
    public void awaitWritable() throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            return;
        }
        if(outstandingTasks > 0) {
            synchronized (this) {
                if(outstandingTasks > 0) {
                    try {
                        this.wait();
                        return;
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                }
            }
        }
        if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
            awaitReadable();
            return;
        }
        sink.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if(anyAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            return;
        }
        if(outstandingTasks > 0) {
            synchronized (this) {
                if(outstandingTasks > 0) {
                    try {
                        this.wait(timeUnit.toMillis(time));
                        return;
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                }
            }
        }
        if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
            awaitReadable(time, timeUnit);
            return;
        }
        sink.awaitWritable();
    }

    @Override
    public XnioIoThread getWriteThread() {
        return delegate.getIoThread();
    }

    @Override
    public void setWriteReadyHandler(WriteReadyHandler handler) {
        delegate.getSinkChannel().getConduit().setWriteReadyHandler(writeReadyHandler = new SslWriteReadyHandler(handler));
    }

    @Override
    public void truncateWrites() throws IOException {
        notifyWriteClosed();
    }

    @Override
    public boolean flush() throws IOException {
        if(anyAreSet(state, FLAG_DELEGATE_SINK_SHUTDOWN)) {
            return sink.flush();
        }
        if(wrappedData != null) {
            doWrap(null, 0, 0);
            if(wrappedData != null) {
                return false;
            }
        }
        if(allAreSet(state, FLAG_WRITE_SHUTDOWN)) {
            if(allAreClear(state, FLAG_ENGINE_OUTBOUND_SHUTDOWN)) {
                state |= FLAG_ENGINE_OUTBOUND_SHUTDOWN;
                engine.closeOutbound();
                doWrap(null, 0, 0);
                if(wrappedData != null) {
                    return false;
                }
            } else if(wrappedData != null && allAreClear(state, FLAG_DELEGATE_SINK_SHUTDOWN)) {
                doWrap(null, 0, 0);
                if(wrappedData != null) {
                    return false;
                }
            }
            if(allAreClear(state, FLAG_DELEGATE_SINK_SHUTDOWN)) {
                sink.terminateWrites();
                state |= FLAG_DELEGATE_SINK_SHUTDOWN;
            }
        }
        return sink.flush();
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if(anyAreSet(state, FLAG_READ_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if(anyAreSet(state, FLAG_READ_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if(anyAreSet(state, FLAG_READ_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return (int) doUnwrap(new ByteBuffer[]{dst}, 0, 1);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        if(anyAreSet(state, FLAG_READ_SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return doUnwrap(dsts, offs, len);
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    void notifyWriteClosed() {
        if(anyAreSet(state, FLAG_WRITE_CLOSED)) {
            return;
        }
        connection.writeClosed();
        state |= FLAG_WRITE_CLOSED;
        if(anyAreSet(state, FLAG_READ_CLOSED)) {
            closed();
        }
    }

    void notifyReadClosed() {
        if(anyAreSet(state, FLAG_READ_CLOSED)) {
            return;
        }
        connection.readClosed();

        state |= FLAG_READ_CLOSED;
        if(anyAreSet(state, FLAG_WRITE_CLOSED)) {
            closed();
        }
    }

    public void startHandshake() throws SSLException {
        engine.beginHandshake();
    }

    public SSLSession getSslSession() {
        return engine.getSession();
    }


    /**
     * Force the handshake to continue
     *
     * @throws IOException
     */
    private void doHandshake() throws IOException {
        doUnwrap(null, 0, 0);
        doWrap(null, 0, 0);
    }


    /**
     * Unwrap channel data into the user buffers. If no user buffer is supplied (e.g. during handshaking) then the
     * unwrap will happen into the channels unwrap buffer.
     *
     * If some data has already been unwrapped it will simply be copied into the user buffers
     * and no unwrap will actually take place.
     *
     * @return true if the unwrap operation made progress, false otherwise
     * @throws SSLException
     */
    private long doUnwrap(ByteBuffer[] userBuffers, int off, int len) throws IOException {
        if(anyAreSet(state, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        if(outstandingTasks > 0) {
            return 0;
        }
        if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
            doWrap(null, 0, 0);
            if(allAreClear(state, FLAG_WRITE_REQUIRES_READ)) { //unless a wrap is immediately required we just return
                return 0;
            }
        }

        Pooled<ByteBuffer> unwrappedData = this.unwrappedData;
        //copy any exiting data
        if(unwrappedData != null && userBuffers != null) {
            long copied = Buffers.copy(userBuffers, off, len, unwrappedData.getResource());
            if(!unwrappedData.getResource().hasRemaining()) {
                unwrappedData.free();
                this.unwrappedData = null;
            }
            return copied;
        }
        try {
            //try and read some data if we don't already have some
            if(allAreClear(state, FLAG_DATA_TO_UNWRAP)) {
                if(dataToUnwrap == null) {
                    dataToUnwrap = bufferPool.allocate();
                }
                int res;
                try {
                    res = source.read(dataToUnwrap.getResource());
                } catch (IOException e) {
                    dataToUnwrap.free();
                    dataToUnwrap = null;
                    throw e;
                }
                dataToUnwrap.getResource().flip();
                if(res == -1) {
                    dataToUnwrap.free();
                    dataToUnwrap = null;
                    notifyReadClosed();
                    return -1;
                } else if(res == 0 && engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                    return 0;
                }
            }
            long original = 0;
            if(userBuffers != null) {
                original = Buffers.remaining(userBuffers);
            }
            //perform the actual unwrap operation
            //if possible this is done into the the user buffers, however
            //if none are supplied or this results in a buffer overflow then we allocate our own
            SSLEngineResult result;
            boolean unwrapBufferUsed = false;
            try {
                if (userBuffers != null) {
                    result = engine.unwrap(dataToUnwrap.getResource(), userBuffers, off, len);
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        //not enough space in the user buffers
                        //we use our own
                        unwrappedData = bufferPool.allocate();
                        ByteBuffer[] d = new ByteBuffer[len + 1];
                        System.arraycopy(userBuffers, off, d, 0, len);
                        d[len] = unwrappedData.getResource();
                        result = engine.unwrap(dataToUnwrap.getResource(), d);
                        unwrapBufferUsed = true;
                    }
                } else {
                    unwrapBufferUsed = true;
                    if (unwrappedData == null) {
                        unwrappedData = bufferPool.allocate();
                    } else {
                        unwrappedData.getResource().compact();
                    }
                    result = engine.unwrap(dataToUnwrap.getResource(), unwrappedData.getResource());
                }
            } finally {
                if(unwrapBufferUsed) {
                    unwrappedData.getResource().flip();
                    if(!unwrappedData.getResource().hasRemaining()) {
                        unwrappedData.free();
                        unwrappedData = null;
                    }
                }
                this.unwrappedData = unwrappedData;
            }

            if (!handleHandshakeResult(result)) {
                if(dataToUnwrap.getResource().hasRemaining()) {
                    state |= FLAG_DATA_TO_UNWRAP;
                }
                return 0;
            }
            if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                notifyReadClosed();
                return -1;
            }
            if(result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                state &= ~FLAG_DATA_TO_UNWRAP;
            } else if(result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new IOException("overflow"); //todo: handle properly
            } else if(dataToUnwrap.getResource().hasRemaining()) {
                state |= FLAG_DATA_TO_UNWRAP;
            }
            if(userBuffers == null) {
                return 0;
            } else {
                return original - Buffers.remaining(userBuffers);
            }
        } finally {
            boolean requiresListenerInvocation = false; //if there is data in the buffer and reads are resumed we should re-run the listener
            if (unwrappedData != null && unwrappedData.getResource().hasRemaining()) {
                requiresListenerInvocation = true;
            }
            if(dataToUnwrap != null) {
                //if there is no data in the buffer we just free it
                if(!dataToUnwrap.getResource().hasRemaining()) {
                    dataToUnwrap.free();
                    dataToUnwrap = null;
                    state &= ~FLAG_DATA_TO_UNWRAP;
                } else if(allAreClear(state, FLAG_DATA_TO_UNWRAP)) {
                    //if there is not enough data in the buffer we compact it to make room for more
                    dataToUnwrap.getResource().compact();
                } else {
                    //there is more data, make sure we trigger a read listener invocation
                    requiresListenerInvocation = true;
                }
            }
            if(requiresListenerInvocation && anyAreSet(state, FLAG_READS_RESUMED)) {
                runReadListener();
            }
        }
    }

    /**
     * Wraps the user data and attempts to send it to the remote client. If data has already been buffered then
     * this is attempted to be sent first.
     *
     * If the supplied buffers are null then a wrap operation is still attempted, which will happen during the
     * handshaking process.
     * @param userBuffers The buffers
     * @param off         The offset
     * @param len         The length
     * @return
     * @throws IOException
     */
    private long doWrap(ByteBuffer[] userBuffers, int off, int len) throws IOException {
        if(anyAreSet(state, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        if(outstandingTasks > 0) {
            return 0;
        }
        if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
            doUnwrap(null, 0, 0);
            if(allAreClear(state, FLAG_READ_REQUIRES_WRITE)) { //unless a wrap is immediatly required we just return
                return 0;
            }
        }
        if(wrappedData != null) {
            int res = sink.write(wrappedData.getResource());
            if(res == 0 || wrappedData.getResource().hasRemaining()) {
                return 0;
            }
            wrappedData.getResource().clear();
        } else {
            wrappedData = bufferPool.allocate();
        }
        try {
            SSLEngineResult result = null;
            while (result == null || (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP && result.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW)) {
                if (userBuffers == null) {
                    result = engine.wrap(EMPTY_BUFFER, wrappedData.getResource());
                } else {
                    result = engine.wrap(userBuffers, off, len, wrappedData.getResource());
                }
            }
            wrappedData.getResource().flip();

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                throw new IOException("underflow"); //todo: can this happen?
            } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                if(!wrappedData.getResource().hasRemaining()) { //if an earlier wrap suceeded we ignore this
                    throw new IOException("overflow"); //todo: handle properly
                }
            }
            //attempt to write it out, if we fail we just return
            //we ignore the handshake status, as wrap will get called again
            int res = sink.write(wrappedData.getResource());
            if(wrappedData.getResource().hasRemaining()) {
                return result.bytesConsumed();
            }

            if (!handleHandshakeResult(result)) {
                return 0;
            }
            if (result.getStatus() == SSLEngineResult.Status.CLOSED && userBuffers != null) {
                notifyWriteClosed();
                throw new ClosedChannelException();
            }

            return result.bytesConsumed();
        } finally {
            //this can be cleared if the channel is fully closed
            if(wrappedData != null) {
                if (!wrappedData.getResource().hasRemaining()) {
                    wrappedData.free();
                    wrappedData = null;
                }
            }
        }
    }

    private boolean handleHandshakeResult(SSLEngineResult result) throws IOException {
        switch (result.getHandshakeStatus()) {
            case NEED_TASK: {
                state |= FLAG_IN_HANDSHAKE;
                clearReadRequiresWrite();
                clearWriteRequiresRead();
                runTasks();
                return false;
            }
            case NEED_UNWRAP: {
                clearReadRequiresWrite();
                state |= FLAG_WRITE_REQUIRES_READ | FLAG_IN_HANDSHAKE;
                sink.suspendWrites();
                if(anyAreSet(state, FLAG_WRITES_RESUMED)) {
                    source.resumeReads();
                }
                if (anyAreSet(state, FLAG_DATA_TO_UNWRAP) && anyAreSet(state, FLAG_WRITES_RESUMED | FLAG_READS_RESUMED)) {
                    runReadListener();
                }

                return false;
            }
            case NEED_WRAP: {
                clearWriteRequiresRead();
                state |= FLAG_READ_REQUIRES_WRITE | FLAG_IN_HANDSHAKE;
                source.suspendReads();
                if(anyAreSet(state, FLAG_READS_RESUMED)) {
                    sink.resumeWrites();
                }
                return false;
            }
            case FINISHED: {
                if(anyAreSet(state, FLAG_IN_HANDSHAKE)) {
                    state &= ~FLAG_IN_HANDSHAKE;
                    handshakeCallback.run();
                }
            }
        }
        clearReadRequiresWrite();
        clearWriteRequiresRead();
        return true;
    }

    private void clearReadRequiresWrite() {
        if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
            state &= ~FLAG_READ_REQUIRES_WRITE;
            if(anyAreSet(state, FLAG_READS_RESUMED)) {
                resumeReads();
            }
            if(allAreClear(state, FLAG_WRITES_RESUMED)) {
                sink.suspendWrites();
            }
        }
    }

    private void clearWriteRequiresRead() {
        if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
            state &= ~FLAG_WRITE_REQUIRES_READ;
            if(anyAreSet(state, FLAG_WRITES_RESUMED)) {
                wakeupWrites();
            }
            if(allAreClear(state, FLAG_READS_RESUMED)) {
                source.suspendReads();
            }
        }
    }

    private void closed() {
        if(anyAreSet(state, FLAG_CLOSED)) {
            return;
        }
        state |= FLAG_CLOSED | FLAG_DELEGATE_SINK_SHUTDOWN | FLAG_DELEGATE_SOURCE_SHUTDOWN | FLAG_WRITE_SHUTDOWN | FLAG_READ_SHUTDOWN;
        notifyReadClosed();
        notifyWriteClosed();
        if(dataToUnwrap != null) {
            dataToUnwrap.free();
            dataToUnwrap = null;
        }
        if(unwrappedData != null) {
            unwrappedData.free();
            unwrappedData = null;
        }
        if(wrappedData != null) {
            wrappedData.free();
            wrappedData = null;
        }
        if(allAreClear(state, FLAG_ENGINE_OUTBOUND_SHUTDOWN)) {
            engine.closeOutbound();
        }
        if(allAreClear(state, FLAG_ENGINE_INBOUND_SHUTDOWN)) {
            try {
                engine.closeInbound();
            } catch (SSLException e) {
                UndertowLogger.REQUEST_LOGGER.ioException(e);
            }
        }
        IoUtils.safeClose(delegate);
    }

    /**
     * Execute all the tasks in the worker
     *
     * Once they are complete we notify any waiting threads and wakeup reads/writes as appropriate
     */
    private void runTasks() {
        //don't run anything in the IO thread till the tasks are done
        delegate.getSinkChannel().suspendWrites();
        delegate.getSourceChannel().suspendReads();
        List<Runnable> tasks = new ArrayList<>();
        Runnable t = engine.getDelegatedTask();
        while (t != null) {
            tasks.add(t);
            t = engine.getDelegatedTask();
        }

        synchronized (this) {
            outstandingTasks += tasks.size();
            for (final Runnable task : tasks) {
                getWorker().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            task.run();
                        } finally {
                            synchronized (SslConduit.this) {
                                if (outstandingTasks == 1) {
                                    getWriteThread().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            synchronized (SslConduit.this) {
                                                SslConduit.this.notifyAll();

                                                --outstandingTasks;
                                                try {
                                                    doHandshake();
                                                } catch (IOException e) {
                                                    IoUtils.safeClose(connection);
                                                }
                                                if (anyAreSet(state, FLAG_READS_RESUMED)) {
                                                    wakeupReads(); //wakeup, because we need to run an unwrap even if there is no data to be read
                                                }
                                                if (anyAreSet(state, FLAG_WRITES_RESUMED)) {
                                                    resumeWrites(); //we don't need to wakeup, as the channel should be writable
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    outstandingTasks--;
                                }
                            }
                        }

                    }
                });
            }
        }
    }

    public SSLEngine getSSLEngine() {
        return engine;
    }

    /**
     * Read ready handler that deals with read-requires-write semantics
     */
    private class SslReadReadyHandler implements ReadReadyHandler {

        private final ReadReadyHandler delegateHandler;

        private SslReadReadyHandler(ReadReadyHandler delegateHandler) {
            this.delegateHandler = delegateHandler;
        }

        @Override
        public void readReady() {
            if(anyAreSet(state, FLAG_WRITE_REQUIRES_READ)) {
                try {
                    doHandshake();
                } catch (IOException e) {
                    UndertowLogger.REQUEST_LOGGER.ioException(e);
                    IoUtils.safeClose(delegate);
                }
            }
            if (anyAreSet(state, FLAG_READS_RESUMED)) {
                if (delegateHandler == null) {
                    final ChannelListener<? super ConduitStreamSourceChannel> readListener = connection.getSourceChannel().getReadListener();
                    if (readListener == null) {
                        suspendReads();
                    } else {
                        ChannelListeners.invokeChannelListener(connection.getSourceChannel(), readListener);
                    }
                } else {
                    delegateHandler.readReady();
                }
            }
            if(!anyAreSet(state, FLAG_READS_RESUMED | FLAG_WRITE_REQUIRES_READ)) {
                delegate.getSourceChannel().suspendReads();
            } else if(anyAreSet(state, FLAG_READS_RESUMED) && (unwrappedData != null || anyAreSet(state, FLAG_DATA_TO_UNWRAP))) {
                if(anyAreSet(state, FLAG_READ_CLOSED)) {
                    if(unwrappedData != null) {
                        unwrappedData.free();
                    }
                    if(dataToUnwrap != null) {
                        dataToUnwrap.free();
                    }
                    unwrappedData = null;
                    dataToUnwrap = null;
                } else {
                    //there is data in the buffers so we do a wakeup
                    //as we may not get an actual read notification
                    runReadListener();
                }
            }
        }

        @Override
        public void forceTermination() {
            try {
                if (delegateHandler != null) {
                    delegateHandler.forceTermination();
                }
            } finally {
                IoUtils.safeClose(delegate);
            }
        }

        @Override
        public void terminated() {
            ChannelListeners.invokeChannelListener(connection.getSourceChannel(), connection.getSourceChannel().getCloseListener());
        }

    }

    /**
     * write read handler that deals with write-requires-read semantics
     */
    private class SslWriteReadyHandler implements WriteReadyHandler {

        private final WriteReadyHandler delegateHandler;

        private SslWriteReadyHandler(WriteReadyHandler delegateHandler) {
            this.delegateHandler = delegateHandler;
        }

        @Override
        public void forceTermination() {
            try {
                if (delegateHandler != null) {
                    delegateHandler.forceTermination();
                }
            } finally {
                IoUtils.safeClose(delegate);
            }
        }

        @Override
        public void terminated() {
            ChannelListeners.invokeChannelListener(connection.getSinkChannel(), connection.getSinkChannel().getCloseListener());
        }

        @Override
        public void writeReady() {
            if(anyAreSet(state, FLAG_READ_REQUIRES_WRITE)) {
                try {
                    doHandshake();
                } catch (IOException e) {
                    UndertowLogger.REQUEST_LOGGER.ioException(e);
                    IoUtils.safeClose(delegate);
                }
            }
            if (anyAreSet(state, FLAG_WRITES_RESUMED)) {
                if(delegateHandler == null) {
                        final ChannelListener<? super ConduitStreamSinkChannel> writeListener = connection.getSinkChannel().getWriteListener();
                        if (writeListener == null) {
                            suspendWrites();
                        } else {
                            ChannelListeners.invokeChannelListener(connection.getSinkChannel(), writeListener);
                        }
                } else {
                    delegateHandler.writeReady();
                }
            }
            if(!anyAreSet(state, FLAG_WRITES_RESUMED | FLAG_READ_REQUIRES_WRITE)) {
                delegate.getSinkChannel().suspendWrites();
            }
        }
    }

    @Override
    public String toString() {
        return "SslConduit{" +
                "state=" + state +
                ", outstandingTasks=" + outstandingTasks +
                ", wrappedData=" + wrappedData +
                ", dataToUnwrap=" + dataToUnwrap +
                ", unwrappedData=" + unwrappedData +
                '}';
    }
}
