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

package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;


/**
 * @author Stuart Douglas
 */
public class Transfer {

    /**
     * Initiate a low-copy transfer between two stream channels.  The pool should be a direct buffer pool for best
     * performance.
     *
     * @param source the source channel
     * @param sink the target channel
     * @param sourceListener the source listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param sinkListener the target listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param readExceptionHandler the read exception handler to call if an error occurs during a read operation
     * @param writeExceptionHandler the write exception handler to call if an error occurs during a write operation
     * @param pool the pool from which the transfer buffer should be allocated
     */
    public static <I extends StreamSourceChannel, O extends StreamSinkChannel> void initiateTransfer(final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super I> readExceptionHandler, final ChannelExceptionHandler<? super O> writeExceptionHandler, ByteBufferPool pool) {
        if (pool == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("pool");
        }
        final PooledByteBuffer allocated = pool.allocate();
        boolean free = true;
        try {
            final ByteBuffer buffer = allocated.getBuffer();
            long read;
            for(;;) {
                try {
                    read = source.read(buffer);
                    buffer.flip();
                } catch (IOException e) {
                    ChannelListeners.invokeChannelExceptionHandler(source, readExceptionHandler, e);
                    return;
                }
                if (read == 0 && !buffer.hasRemaining()) {
                    break;
                }
                if (read == -1 && !buffer.hasRemaining()) {
                    done(source, sink, sourceListener, sinkListener);
                    return;
                }
                while (buffer.hasRemaining()) {
                    final int res;
                    try {
                        res = sink.write(buffer);
                    } catch (IOException e) {
                        ChannelListeners.invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
                        return;
                    }
                    if (res == 0) {
                        break;
                    }
                }
                if(buffer.hasRemaining()) {
                    break;
                }
                buffer.clear();
            }
            PooledByteBuffer current = null;
            if(buffer.hasRemaining()) {
                current = allocated;
                free = false;
            }

            final TransferListener<I, O> listener = new TransferListener<>(pool, current, source, sink, sourceListener, sinkListener, writeExceptionHandler, readExceptionHandler, read == -1);
            sink.getWriteSetter().set(listener);
            source.getReadSetter().set(listener);
            //we resume both reads and writes, as we want to keep trying to fill the buffer
            if(current == null || buffer.capacity() != buffer.remaining()) {
                //we don't resume if the buffer is 100% full
                source.resumeReads();
            }
            if(current != null) {
                //we don't resume writes if we have nothing to write
                sink.resumeWrites();
            }
        } finally {
            if (free) {
                allocated.close();
            }
        }
    }

    private static <I extends StreamSourceChannel, O extends StreamSinkChannel> void done(I source, O sink, ChannelListener<? super I> sourceListener, ChannelListener<? super O> sinkListener) {
        Channels.setReadListener(source, sourceListener);
        if (sourceListener == null) {
            source.suspendReads();
        } else {
            source.wakeupReads();
        }

        Channels.setWriteListener(sink, sinkListener);
        if (sinkListener == null) {
            sink.suspendWrites();
        } else {
            sink.wakeupWrites();
        }
    }

    static final class TransferListener<I extends StreamSourceChannel, O extends StreamSinkChannel> implements ChannelListener<Channel> {
        private PooledByteBuffer pooledBuffer;
        private final ByteBufferPool pool;
        private final I source;
        private final O sink;
        private final ChannelListener<? super I> sourceListener;
        private final ChannelListener<? super O> sinkListener;
        private final ChannelExceptionHandler<? super O> writeExceptionHandler;
        private final ChannelExceptionHandler<? super I> readExceptionHandler;
        private boolean sourceDone;
        private boolean done = false;

        TransferListener(ByteBufferPool pool, final PooledByteBuffer pooledBuffer, final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super O> writeExceptionHandler, final ChannelExceptionHandler<? super I> readExceptionHandler, boolean sourceDone) {
            this.pool = pool;
            this.pooledBuffer = pooledBuffer;
            this.source = source;
            this.sink = sink;
            this.sourceListener = sourceListener;
            this.sinkListener = sinkListener;
            this.writeExceptionHandler = writeExceptionHandler;
            this.readExceptionHandler = readExceptionHandler;
            this.sourceDone = sourceDone;
        }

        public void handleEvent(final Channel channel) {
            if(done) {
                if(channel instanceof StreamSinkChannel) {
                    ((StreamSinkChannel) channel).suspendWrites();
                } else if(channel instanceof StreamSourceChannel) {
                    ((StreamSourceChannel)channel).suspendReads();
                }
                return;
            }
            boolean noWrite = false;
            if (pooledBuffer == null) {
                pooledBuffer = pool.allocate();
                noWrite = true;
            } else if(channel instanceof StreamSourceChannel) {
                noWrite = true; //attempt a read first, as this is a read notification
                pooledBuffer.getBuffer().compact();
            }

            final ByteBuffer buffer = pooledBuffer.getBuffer();
            try {
                long read;

                for(;;) {
                    boolean writeFailed = false;
                    //always attempt to write first if we have the buffer
                    if(!noWrite) {
                        while (buffer.hasRemaining()) {
                            final int res;
                            try {
                                res = sink.write(buffer);
                            } catch (IOException e) {
                                pooledBuffer.close();
                                pooledBuffer = null;
                                done = true;
                                ChannelListeners.invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
                                return;
                            }
                            if (res == 0) {
                                writeFailed = true;
                                break;
                            }
                        }
                        if(sourceDone && !buffer.hasRemaining()) {
                            done = true;
                            done(source, sink, sourceListener, sinkListener);
                            return;
                        }
                        buffer.compact();
                    }
                    noWrite = false;

                    if(buffer.hasRemaining() && !sourceDone) {
                        try {
                            read = source.read(buffer);
                            buffer.flip();
                        } catch (IOException e) {
                            pooledBuffer.close();
                            pooledBuffer = null;
                            done = true;
                            ChannelListeners.invokeChannelExceptionHandler(source, readExceptionHandler, e);
                            return;
                        }
                        if (read == 0) {
                            break;
                        } else if(read == -1) {
                            sourceDone = true;
                            if (!buffer.hasRemaining()) {
                                done = true;
                                done(source, sink, sourceListener, sinkListener);
                                return;
                            }
                        }
                    } else {
                        buffer.flip();
                        if(writeFailed) {
                            break;
                        }
                    }

                }
                //suspend writes if there is nothing to write
                if(!buffer.hasRemaining()) {
                    sink.suspendWrites();
                } else if(!sink.isWriteResumed()) {
                    sink.resumeWrites();
                }
                //suspend reads if there is nothing to read
                if(buffer.remaining() == buffer.capacity()) {
                    source.suspendReads();
                } else if(!source.isReadResumed()){
                    source.resumeReads();
                }
            } finally {
                if (pooledBuffer != null && !buffer.hasRemaining()) {
                    pooledBuffer.close();
                    pooledBuffer = null;
                }
            }
        }

        public String toString() {
            return "Transfer channel listener (" + source + " to " + sink + ") -> (" + sourceListener + " and " + sinkListener + ")";
        }
    }

}
