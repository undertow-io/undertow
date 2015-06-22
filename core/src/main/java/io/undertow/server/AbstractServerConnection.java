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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractServerConnection  extends ServerConnection {
    protected final StreamConnection channel;
    protected final CloseSetter closeSetter;
    protected final ByteBufferPool bufferPool;
    protected final HttpHandler rootHandler;
    protected final OptionMap undertowOptions;
    protected final StreamSourceConduit originalSourceConduit;
    protected final StreamSinkConduit originalSinkConduit;
    protected final List<CloseListener> closeListeners = new LinkedList<>();

    protected HttpServerExchange current;

    private final int bufferSize;

    private XnioBufferPoolAdaptor poolAdaptor;

    /**
     * Any extra bytes that were read from the channel. This could be data for this requests, or the next response.
     */
    protected PooledByteBuffer extraBytes;

    public AbstractServerConnection(StreamConnection channel, final ByteBufferPool bufferPool, final HttpHandler rootHandler, final OptionMap undertowOptions, final int bufferSize) {
        this.channel = channel;
        this.bufferPool = bufferPool;
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        closeSetter = new CloseSetter();
        if (channel != null) {
            this.originalSinkConduit = channel.getSinkChannel().getConduit();
            this.originalSourceConduit = channel.getSourceChannel().getConduit();
            channel.setCloseListener(closeSetter);
        } else {
            this.originalSinkConduit = null;
            this.originalSourceConduit = null;
        }
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        if(poolAdaptor == null) {
            poolAdaptor = new XnioBufferPoolAdaptor(getByteBufferPool());
        }
        return poolAdaptor;
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
    @Override
    public ByteBufferPool getByteBufferPool() {
        return bufferPool;
    }

    /**
     * Get the underlying channel.
     *
     * @return the underlying channel
     */
    public StreamConnection getChannel() {
        return channel;
    }

    @Override
    public ChannelListener.Setter<ServerConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        if(channel == null) {
            return null;
        }
        return channel.getIoThread();
    }


    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    /**
     * @return The size of the buffers allocated by the buffer pool
     */
    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    public PooledByteBuffer getExtraBytes() {
        if(extraBytes != null && !extraBytes.getBuffer().hasRemaining()) {
            extraBytes.close();
            extraBytes = null;
            return null;
        }
        return extraBytes;
    }

    public void setExtraBytes(final PooledByteBuffer extraBytes) {
        this.extraBytes = extraBytes;
    }

    /**
     * @return The original source conduit
     */
    public StreamSourceConduit getOriginalSourceConduit() {
        return originalSourceConduit;
    }

    /**
     * @return The original underlying sink conduit
     */
    public StreamSinkConduit getOriginalSinkConduit() {
        return originalSinkConduit;
    }

    /**
     * Resets the channel to its original state, effectively disabling all current conduit
     * wrappers. The current state is encapsulated inside a {@link ConduitState} object that
     * can be used the restore the channel.
     *
     * @return An opaque representation of the previous channel state
     */
    public ConduitState resetChannel() {
        ConduitState ret = new ConduitState(channel.getSinkChannel().getConduit(), channel.getSourceChannel().getConduit());
        channel.getSinkChannel().setConduit(originalSinkConduit);
        channel.getSourceChannel().setConduit(originalSourceConduit);
        return ret;
    }

    /**
     * Resets the channel to its original state, effectively disabling all current conduit
     * wrappers. The current state is lost.
     */
    public void clearChannel() {
        channel.getSinkChannel().setConduit(originalSinkConduit);
        channel.getSourceChannel().setConduit(originalSourceConduit);
    }
    /**
     * Restores the channel conduits to a previous state.
     *
     * @param state The original state
     * @see #resetChannel()
     */
    public void restoreChannel(final ConduitState state) {
        channel.getSinkChannel().setConduit(state.sink);
        channel.getSourceChannel().setConduit(state.source);
    }

    public static class ConduitState {
        final StreamSinkConduit sink;
        final StreamSourceConduit source;

        private ConduitState(final StreamSinkConduit sink, final StreamSourceConduit source) {
            this.sink = sink;
            this.source = source;
        }
    }

    protected static StreamSinkConduit sink(ConduitState state) {
        return state.sink;
    }

    protected static StreamSourceConduit source(ConduitState state) {
        return state.source;
    }

    @Override
    public void addCloseListener(CloseListener listener) {
        this.closeListeners.add(listener);
    }

    @Override
    protected ConduitStreamSinkChannel getSinkChannel() {
        return channel.getSinkChannel();
    }

    @Override
    protected ConduitStreamSourceChannel getSourceChannel() {
        return channel.getSourceChannel();
    }

    protected void setUpgradeListener(HttpUpgradeListener upgradeListener) {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    protected void maxEntitySizeUpdated(HttpServerExchange exchange) {
    }

    private class CloseSetter implements ChannelListener.Setter<ServerConnection>, ChannelListener<StreamConnection> {

        private ChannelListener<? super ServerConnection> listener;

        @Override
        public void set(ChannelListener<? super ServerConnection> listener) {
            this.listener = listener;
        }

        @Override
        public void handleEvent(StreamConnection channel) {
            try {
                for (CloseListener l : closeListeners) {
                    try {
                        l.closed(AbstractServerConnection.this);
                    } catch (Throwable e) {
                        UndertowLogger.REQUEST_LOGGER.exceptionInvokingCloseListener(l, e);
                    }
                }
                if (current != null) {
                    current.endExchange();
                }
                ChannelListeners.invokeChannelListener(AbstractServerConnection.this, listener);
            } finally {
                if(extraBytes != null) {
                    extraBytes.close();
                    extraBytes = null;
                }
            }
        }
    }
}
