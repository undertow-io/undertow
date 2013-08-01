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
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLSession;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * A server-side HTTP connection.
 * <p/>
 * Note that the lifecycle of the server connection is tied to the underlying TCP connection. Even if the channel
 * is upgraded the connection is not considered closed until the upgraded channel is closed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerConnection extends AbstractAttachable implements ServerConnection {
    private final StreamConnection channel;
    private final CloseSetter closeSetter;
    private final Pool<ByteBuffer> bufferPool;
    private final HttpHandler rootHandler;
    private final OptionMap undertowOptions;
    private final StreamSourceConduit originalSourceConduit;
    private final StreamSinkConduit originalSinkConduit;
    private final List<CloseListener> closeListeners = new LinkedList<CloseListener>();

    private final int bufferSize;
    /**
     * Any extra bytes that were read from the channel. This could be data for this requests, or the next response.
     */
    private Pooled<ByteBuffer> extraBytes;

    public HttpServerConnection(StreamConnection channel, final Pool<ByteBuffer> bufferPool, final HttpHandler rootHandler, final OptionMap undertowOptions, final int bufferSize) {
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
    public Pool<ByteBuffer> getBufferPool() {
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
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        if(exchange == null || !HttpContinue.requiresContinueResponse(exchange)) {
            throw UndertowMessages.MESSAGES.outOfBandResponseOnlyAllowedFor100Continue();
        }
        final ConduitState state = resetChannel();
        HttpServerExchange newExchange =  new HttpServerExchange(this);
        for(HttpString header : exchange.getRequestHeaders().getHeaderNames()) {
            newExchange.getRequestHeaders().putAll(header, exchange.getRequestHeaders().get(header));
        }
        newExchange.setProtocol(exchange.getProtocol());
        newExchange.setRequestMethod(exchange.getRequestMethod());
        newExchange.setParsedRequestPath(exchange.getRequestPath());
        newExchange.getRequestHeaders().put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
        newExchange.getRequestHeaders().put(Headers.CONTENT_LENGTH, 0);

        //apply transfer encoding rules
        HttpTransferEncoding.setupRequest(newExchange);

        newExchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                restoreChannel(state);
            }
        });
        return newExchange;
    }

    /**
     * Pushes back the given data. This should only be used by transfer coding handlers that have read past
     * the end of the request when handling pipelined requests
     *
     * @param unget The buffer to push back
     */
    public void ungetRequestBytes(final Pooled<ByteBuffer> unget) {
        if (getExtraBytes() == null) {
            setExtraBytes(unget);
        } else {
            Pooled<ByteBuffer> eb = getExtraBytes();
            ByteBuffer buf = eb.getResource();
            final ByteBuffer ugBuffer = unget.getResource();

            if (ugBuffer.limit() - ugBuffer.remaining() > buf.remaining()) {
                //stuff the existing data after the data we are ungetting
                ugBuffer.compact();
                ugBuffer.put(buf);
                ugBuffer.flip();
                eb.free();
                setExtraBytes(unget);
            } else {
                //TODO: this is horrible, but should not happen often
                final byte[] data = new byte[ugBuffer.remaining() + buf.remaining()];
                int first = ugBuffer.remaining();
                ugBuffer.get(data, 0, ugBuffer.remaining());
                buf.get(data, first, buf.remaining());
                eb.free();
                unget.free();
                final ByteBuffer newBuffer = ByteBuffer.wrap(data);
                setExtraBytes(new Pooled<ByteBuffer>() {
                    @Override
                    public void discard() {

                    }

                    @Override
                    public void free() {

                    }

                    @Override
                    public ByteBuffer getResource() throws IllegalStateException {
                        return newBuffer;
                    }
                });
            }
        }
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

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        if (channel instanceof SslChannel) {
            return new DefaultSslSessionInfo(((SslChannel) channel).getSslSession());
        }
        return null;
    }

    public SSLSession getSslSession() {
        if (channel instanceof SslChannel) {
            return ((SslChannel) channel).getSslSession();
        }
        return null;
    }

    public Pooled<ByteBuffer> getExtraBytes() {
        return extraBytes;
    }

    public void setExtraBytes(final Pooled<ByteBuffer> extraBytes) {
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
     * Resores the channel conduits to a previous state.
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

    @Override
    public void addCloseListener(CloseListener listener) {
        this.closeListeners.add(listener);
    }

    @Override
    public StreamConnection upgradeChannel() {
        resetChannel();
        if(extraBytes != null) {
            channel.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(channel.getSourceChannel().getConduit(), this));
        }
        return channel;
    }

    @Override
    public ConduitStreamSinkChannel getSinkChannel() {
        return channel.getSinkChannel();
    }

    @Override
    public ConduitStreamSourceChannel getSourceChannel() {
        return channel.getSourceChannel();
    }

    private class CloseSetter implements ChannelListener.Setter<ServerConnection>, ChannelListener<StreamConnection> {

        private ChannelListener<? super ServerConnection> listener;

        @Override
        public void set(ChannelListener<? super ServerConnection> listener) {
            this.listener = listener;
        }

        @Override
        public void handleEvent(StreamConnection channel) {
            for (CloseListener l : closeListeners) {
                try {
                    l.closed(HttpServerConnection.this);
                } catch (Throwable e) {
                    UndertowLogger.REQUEST_LOGGER.exceptionInvokingCloseListener(l, e);
                }
            }
            ChannelListeners.invokeChannelListener(HttpServerConnection.this, listener);
        }
    }
}
