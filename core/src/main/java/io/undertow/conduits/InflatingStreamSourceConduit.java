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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;
import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.NewInstanceObjectPool;
import io.undertow.util.ObjectPool;
import io.undertow.util.PooledObject;
import io.undertow.util.SimpleObjectPool;

/**
 * @author Stuart Douglas
 */
public class InflatingStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    public static final ConduitWrapper<StreamSourceConduit> WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
        @Override
        public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
            return new InflatingStreamSourceConduit(exchange, factory.create());
        }
    };

    private volatile Inflater inflater;

    private final PooledObject<Inflater> pooledObject;
    private final HttpServerExchange exchange;
    private PooledByteBuffer compressed;
    private PooledByteBuffer uncompressed;
    private boolean nextDone = false;
    private boolean headerDone = false;

    public InflatingStreamSourceConduit(HttpServerExchange exchange, StreamSourceConduit next) {
        this(exchange, next, newInstanceInflaterPool());
    }

    public InflatingStreamSourceConduit(
            HttpServerExchange exchange,
            StreamSourceConduit next,
            ObjectPool<Inflater> inflaterPool) {
        super(next);
        this.exchange = exchange;
        this.pooledObject = inflaterPool.allocate();
        this.inflater = pooledObject.getObject();
    }

    public static ObjectPool<Inflater> newInstanceInflaterPool() {
        return new NewInstanceObjectPool<>(() -> new Inflater(true), Inflater::end);
    }

    public static ObjectPool<Inflater> simpleInflaterPool(int poolSize) {
        return new SimpleObjectPool<>(poolSize, () -> new Inflater(true), Inflater::reset, Inflater::end);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (isReadShutdown()) {
            throw new ClosedChannelException();
        }
        if (uncompressed != null) {
            int ret = Buffers.copy(dst, uncompressed.getBuffer());
            if (!uncompressed.getBuffer().hasRemaining()) {
                uncompressed.close();
                uncompressed = null;
            }
            return ret;
        }
        for(;;) {
            if (compressed == null && !nextDone) {
                compressed = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate();
                ByteBuffer buf = compressed.getBuffer();
                int res = next.read(buf);
                if (res == -1) {
                    nextDone = true;
                    compressed.close();
                    compressed = null;
                } else if (res == 0) {
                    compressed.close();
                    compressed = null;
                    return 0;
                } else {
                    buf.flip();
                    if (!headerDone) {
                        headerDone = readHeader(buf);
                    }
                    inflater.setInput(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                }
            }
            if (nextDone && inflater.needsInput() && !inflater.finished()) {
                throw UndertowLogger.ROOT_LOGGER.unexpectedEndOfCompressedInput();
            } else if (nextDone && inflater.finished()) {
                done();
                return -1;
            } else if (inflater.finished() && compressed != null) {
                int rem = inflater.getRemaining();
                ByteBuffer buf = compressed.getBuffer();
                buf.position(buf.limit() - rem);
                readFooter(buf);
                int res;
                do {
                    buf.clear();
                    res = next.read(buf);
                    buf.flip();
                    if (res == -1) {
                        done();
                        nextDone = true;
                        return -1;
                    } else if (res > 0) {
                        readFooter(buf);
                    }
                } while (res != 0);
                compressed.close();
                compressed = null;
                return 0;
            } else if (compressed == null) {
                throw new RuntimeException();
            }
            uncompressed = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate();
            try {
                int read = inflater.inflate(uncompressed.getBuffer().array(), uncompressed.getBuffer().arrayOffset(), uncompressed.getBuffer().limit());
                uncompressed.getBuffer().limit(read);
                dataDeflated(uncompressed.getBuffer().array(), uncompressed.getBuffer().arrayOffset(), read);
                if (inflater.needsInput()) {
                    compressed.close();
                    compressed = null;
                }
                int ret = Buffers.copy(dst, uncompressed.getBuffer());
                if (!uncompressed.getBuffer().hasRemaining()) {
                    uncompressed.close();
                    uncompressed = null;
                }
                if(ret > 0) {
                    return ret;
                }
            } catch (DataFormatException e) {
                done();
                throw new IOException(e);
            }
        }
    }

    protected void readFooter(ByteBuffer buf) throws IOException {

    }

    protected boolean readHeader(ByteBuffer byteBuffer) throws IOException {
        return true;
    }

    protected void dataDeflated(byte[] data, int off, int len) {

    }

    private void done() {
        if (compressed != null) {
            compressed.close();
        }
        if (uncompressed != null) {
            uncompressed.close();
        }
        if (inflater != null) {
            pooledObject.close();
            inflater = null;
        }
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        try {
            return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
        } catch (IOException | RuntimeException | Error e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        try {
            return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
        } catch (IOException | RuntimeException | Error e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }


    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        for (int i = offset; i < length; ++i) {
            if (dsts[i].hasRemaining()) {
                return read(dsts[i]);
            }
        }
        return 0;
    }

    @Override
    public void terminateReads() throws IOException {
        done();
        next.terminateReads();
    }
}
