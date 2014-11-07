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

package io.undertow.server.protocol.spdy;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;


/**
 * Open listener for SPDY server
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SpdyOpenListener implements ChannelListener<StreamConnection>, DelegateOpenListener {

    public static final String SPDY_3_1 = "spdy/3.1";

    private final Pool<ByteBuffer> bufferPool;
    private final Pool<ByteBuffer> heapBufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool) {
        this(pool, heapBufferPool, OptionMap.EMPTY);
    }

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool, final OptionMap undertowOptions) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        Pooled<ByteBuffer> buf = pool.allocate();
        this.bufferSize = buf.getResource().remaining();
        buf.free();
        this.heapBufferPool = heapBufferPool;
        Pooled<ByteBuffer> buff = heapBufferPool.allocate();
        try {
            if (!buff.getResource().hasArray()) {
                throw UndertowMessages.MESSAGES.mustProvideHeapBuffer();
            }
        } finally {
            buff.free();
        }
    }

    @Override
    public void handleEvent(StreamConnection channel) {
        handleEvent(channel, null);
    }

    public void handleEvent(final StreamConnection channel, Pooled<ByteBuffer> buffer) {

        //cool, we have a spdy connection.
        SpdyChannel spdyChannel = new SpdyChannel(channel, bufferPool, buffer, heapBufferPool, false);
        Integer idleTimeout = undertowOptions.get(UndertowOptions.IDLE_TIMEOUT);
        if (idleTimeout != null && idleTimeout > 0) {
            spdyChannel.setIdleTimeout(idleTimeout);
        }

        spdyChannel.getReceiveSetter().set(new SpdyReceiveListener(rootHandler, getUndertowOptions(), bufferSize));
        spdyChannel.resumeReceives();
    }

    @Override
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    @Override
    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    @Override
    public void setUndertowOptions(final OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

}
