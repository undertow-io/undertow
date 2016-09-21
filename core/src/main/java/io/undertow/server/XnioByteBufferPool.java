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

package io.undertow.server;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class XnioByteBufferPool implements ByteBufferPool {

    private final Pool<ByteBuffer> pool;
    private final ByteBufferPool arrayBackedPool;
    private final int bufferSize;
    private final boolean direct;

    public XnioByteBufferPool(Pool<ByteBuffer> pool) {
        this.pool = pool;
        Pooled<ByteBuffer> buf = pool.allocate();
        bufferSize = buf.getResource().remaining();
        direct = !buf.getResource().hasArray();
        buf.free();
        if(direct) {
            arrayBackedPool = new XnioByteBufferPool(new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, bufferSize, bufferSize, 0));
        } else {
            arrayBackedPool = this;
        }
    }

    @Override
    public PooledByteBuffer allocate() {
        final Pooled<ByteBuffer> buf = pool.allocate();
        return new PooledByteBuffer() {

            private boolean open = true;

            @Override
            public ByteBuffer getBuffer() {
                return buf.getResource();
            }

            @Override
            public void close() {
                open = false;
                buf.free();
            }

            @Override
            public boolean isOpen() {
                return open;
            }
        };
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return arrayBackedPool;
    }

    @Override
    public void close() {

    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isDirect() {
        return direct;
    }
}
