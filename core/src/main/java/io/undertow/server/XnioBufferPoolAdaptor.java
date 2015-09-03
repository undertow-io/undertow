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
import org.xnio.Pool;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * Adaptor between a ByteBufferPool and an XNIO Pool
 *
 * @author Stuart Douglas
 */
public class XnioBufferPoolAdaptor implements Pool<ByteBuffer> {

    private final ByteBufferPool byteBufferPool;

    public XnioBufferPoolAdaptor(ByteBufferPool byteBufferPool) {
        this.byteBufferPool = byteBufferPool;
    }

    @Override
    public Pooled<ByteBuffer> allocate() {
        final PooledByteBuffer buf = byteBufferPool.allocate();
        return new Pooled<ByteBuffer>() {
            @Override
            public void discard() {
                buf.close();
            }

            @Override
            public void free() {
                buf.close();
            }

            @Override
            public ByteBuffer getResource() throws IllegalStateException {
                return buf.getBuffer();
            }

            @Override
            public void close() {
                buf.close();
            }
        };
    }
}
