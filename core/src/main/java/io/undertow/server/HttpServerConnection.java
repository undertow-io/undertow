/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLSession;

import io.undertow.UndertowOptions;
import io.undertow.util.AbstractAttachable;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * A server-side HTTP connection.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerConnection extends AbstractAttachable implements ConnectedChannel {
    private final ConnectedStreamChannel channel;
    private final ChannelListener.Setter<HttpServerConnection> closeSetter;
    private final Pool<ByteBuffer> bufferPool;
    private final HttpHandler rootHandler;
    private final int maxConcurrentRequests;
    private final OptionMap undertowOptions;
    private final int bufferSize;
    private final SSLSession sslSession;

    @SuppressWarnings("unused")
    private volatile int runningRequestCount = 1;

    private static final AtomicIntegerFieldUpdater<HttpServerConnection> runningRequestCountUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpServerConnection.class, "runningRequestCount");

    public HttpServerConnection(ConnectedStreamChannel channel, final Pool<ByteBuffer> bufferPool, final HttpHandler rootHandler, final OptionMap undertowOptions, final int bufferSize, final SSLSession sslSession) {
        this.channel = channel;
        this.bufferPool = bufferPool;
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.sslSession = sslSession;
        this.maxConcurrentRequests = undertowOptions.get(UndertowOptions.MAX_REQUESTS_PER_CONNECTION, 1);
        closeSetter = ChannelListeners.getDelegatingSetter(channel.getCloseSetter(), this);
    }

    /**
     * Get the root HTTP handler for this connection.
     *
     * @return the root HTTP handler for this connection
     */
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    /**
     * Get the buffer pool for this connection.
     *
     * @return the buffer pool for this connection
     */
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    /**
     * Get the underlying channel.
     *
     * @return the underlying channel
     */
    public ConnectedStreamChannel getChannel() {
        return channel;
    }

    public ChannelListener.Setter<HttpServerConnection> getCloseSetter() {
        return closeSetter;
    }

    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public boolean supportsOption(final Option<?> option) {
        return channel.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    public void close() throws IOException {
        channel.close();
    }

    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }

    /**
     * Attempts to increment the number of running requests. If this would result in more
     * requests running than that specified in {@link #maxConcurrentRequests} then it is not
     * incremented and returns false;
     * @return true if the request is allowed to start, false otherwise
     */
    public boolean startRequest() {
        int running;
        do {
            running = runningRequestCountUpdater.get(this);
            if (running == maxConcurrentRequests) {
                return false;
            }
        } while (!runningRequestCountUpdater.compareAndSet(this, running, running + 1));
        return true;
    }

    /**
     * Decrements the running request count, and returns the new value
     *
     * @return The new running request count
     */
    public void requestFinished() {
        runningRequestCountUpdater.decrementAndGet(this);
    }

    /**
     * @return The maximum number of concurrent requests that can be active at any given time on this connection
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    /**
     *
     * @return The size of the buffers allocated by the buffer pool
     */
    public int getBufferSize() {
        return bufferSize;
    }

    public SSLSession getSslSession() {
        return sslSession;
    }
}
