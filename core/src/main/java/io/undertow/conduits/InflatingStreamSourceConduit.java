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
import io.undertow.UndertowMessages;
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
    private volatile PooledObject<Inflater> activePooledObject;

    private final ObjectPool<Inflater> objectPoolNonWrapping;
    private final ObjectPool<Inflater> objectPoolWrapping;
    private final HttpServerExchange exchange;
    private PooledByteBuffer compressed;
    private PooledByteBuffer uncompressed;
    private boolean nextDone = false;
    private boolean headerDone = false;

    public InflatingStreamSourceConduit(HttpServerExchange exchange, StreamSourceConduit next) {
        this(exchange, next, newInstanceInflaterPool(), newInstanceWrappingInflaterPool());
    }

    public InflatingStreamSourceConduit(
            HttpServerExchange exchange,
            StreamSourceConduit next,
            ObjectPool<Inflater> inflaterPool) {
        this(exchange, next, inflaterPool, newInstanceWrappingInflaterPool());
    }

    public InflatingStreamSourceConduit(
            HttpServerExchange exchange,
            StreamSourceConduit next,
            ObjectPool<Inflater> inflaterPool,
            ObjectPool<Inflater> inflaterWrappingPool) {
        super(next);
        this.exchange = exchange;
        this.objectPoolNonWrapping = inflaterPool;
        this.objectPoolWrapping = inflaterWrappingPool;
    }
    /**
     * Create non-wrapping(gzip/zlib without headers) inflater pool
     * @return
     */
    public static ObjectPool<Inflater> newInstanceInflaterPool() {
        return new NewInstanceObjectPool<Inflater>(() -> new Inflater(true), Inflater::end);
    }

    /**
     * Create non-wrapping(gzip/zlib without headers) inflater pool
     * @return
     */
    public static ObjectPool<Inflater> simpleInflaterPool(int poolSize) {
        return new SimpleObjectPool<Inflater>(poolSize, () -> new Inflater(true), Inflater::reset, Inflater::end);
    }

    /**
     * Create wrapping inflater pool, one that expects headers.
     * @return
     */
    public static ObjectPool<Inflater> newInstanceWrappingInflaterPool(){
        return new NewInstanceObjectPool<>(() -> new Inflater(false), Inflater::end);
    }

    /**
     * Create wrapping inflater pool, one that expects headers.
     * @return
     */
    public static ObjectPool<Inflater> simpleWrappingInflaterPool(int poolSize) {
        return new SimpleObjectPool<>(poolSize, () -> new Inflater(false), Inflater::reset, Inflater::end);
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

                    initializeInflater(buf);
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

    protected void initializeInflater(ByteBuffer buf) throws IOException {
        // ensure the activePooledObject is set only once until done() is called
        // we don't want to reset the inflater state mid-stream
        if (activePooledObject == null) {
            if (isZlibHeaderPresent(buf)) {
                this.activePooledObject = this.objectPoolWrapping.allocate();
            } else {
                this.activePooledObject = this.objectPoolNonWrapping.allocate();
            }
        }
        this.inflater = this.activePooledObject.getObject();
    }

    protected boolean isZlibHeaderPresent(final ByteBuffer buf) throws IOException {
        if(buf.remaining()<2) {
            throw UndertowMessages.MESSAGES.bufferUnderflow(this.exchange, buf);
        }
        // https://www.ietf.org/rfc/rfc1950.txt - 2.2. - Data format, two bytes. Below is sort of a cheat, we have so much power
        //to quickly compress to best cap.
        //      FLEVEL:        0       1       2       3
        //        CINFO:
        //            0      08 1D   08 5B   08 99   08 D7
        //            1      18 19   18 57   18 95   18 D3
        //            2      28 15   28 53   28 91   28 CF
        //            3      38 11   38 4F   38 8D   38 CB
        //            4      48 0D   48 4B   48 89   48 C7
        //            5      58 09   58 47   58 85   58 C3
        //            6      68 05   68 43   68 81   68 DE
        //            7      78 01   78 5E   78 9C   78 DA
        buf.mark();
        final char cmf = (char)(buf.get() & 0xFF);
        final char flg = (char)(buf.get() & 0xFF);
        buf.reset();
        return (cmf == 0x78 && (flg == 0x01 || flg == 0x5E || flg == 0x9c || flg == 0xDA));
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
            activePooledObject.close();
            activePooledObject = null;
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
