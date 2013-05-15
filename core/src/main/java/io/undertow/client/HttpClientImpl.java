/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.client;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import io.undertow.channels.ReadTimeoutStreamSourceChannel;
import io.undertow.channels.WriteTimeoutStreamSinkChannel;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Result;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.SslChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Emanuel Muckenhuber
 */
class HttpClientImpl extends HttpClient {

    private final OptionMap options;
    private final Pool<ByteBuffer> bufferPool;
    // TODO sconnection management
    private final Set<HttpClientConnection> connections = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<HttpClientConnection, Boolean>()));

    HttpClientImpl(final XnioWorker worker, final OptionMap options) {
        super(worker);
        this.options = options;
        this.bufferPool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 1024 * 4, 4096 * 20);
    }

    @Override
    public IoFuture<HttpClientConnection> connect(final SocketAddress destination, final OptionMap optionMap) {
        return connect(null, destination, optionMap);
    }

    @Override
    public IoFuture<HttpClientConnection> connect(final XnioIoThread ioThread, final SocketAddress destination, final OptionMap optionMap) {
        final FutureResult<HttpClientConnection> result = new FutureResult<>();
        result.addCancelHandler(new Cancellable() {
            @Override
            public Cancellable cancel() {
                result.setCancelled();
                return this;
            }
        });
        // Connect
        final ChannelListener<StreamConnection> openListener = new ClientConnectionOpenListener(result, optionMap);
        final IoFuture<StreamConnection> future;
        if (ioThread == null) {
            future = getWorker().openStreamConnection(destination, openListener, optionMap);
        } else {
            future = ioThread.openStreamConnection(destination, openListener, optionMap);
        }
        future.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, IoFuture<HttpClientConnection>>() {
            @Override
            public void handleCancelled(IoFuture<HttpClientConnection> future) {
                future.cancel();
            }

            @Override
            public void handleFailed(IOException exception, IoFuture<HttpClientConnection> attachment) {
                result.setException(exception);
            }
        }, result.getIoFuture());
        return result.getIoFuture();
    }

    @Override
    public void close() throws IOException {
        for (final HttpClientConnection connection : connections) {
            connection.close();
        }
    }

    Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    void connectionClosed(HttpClientConnection connection) {
        connections.remove(connection);
    }

    class ClientConnectionOpenListener implements ChannelListener<StreamConnection> {

        private final Result<HttpClientConnection> result;
        private final OptionMap options;

        ClientConnectionOpenListener(Result<HttpClientConnection> result, OptionMap options) {
            this.result = result;
            this.options = options;
        }

        @Override
        public void handleEvent(StreamConnection channel) {
            StreamSourceChannel readChannel = channel.getSourceChannel();
            StreamSinkChannel writeChannel = channel.getSinkChannel();
            //set read and write timeouts
            if (channel.supportsOption(Options.READ_TIMEOUT)) {
                readChannel = new ReadTimeoutStreamSourceChannel(readChannel);
            }
            if (channel.supportsOption(Options.WRITE_TIMEOUT)) {
                writeChannel = new WriteTimeoutStreamSinkChannel(writeChannel);
            }
            final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(readChannel);
            final AssembledConnectedStreamChannel assembledChannel;
            if (channel instanceof SslChannel) {
                assembledChannel = new AssembledConnectedSslStreamChannel((SslChannel) channel, readChannel, writeChannel);
            } else {
                assembledChannel = new AssembledConnectedStreamChannel(channel, readChannel, writeChannel);
            }
            final HttpClientConnection connection = new HttpClientConnectionImpl(assembledChannel, pushBackStreamChannel, options, HttpClientImpl.this);
            result.setResult(connection);
            connections.add(connection);
        }
    }

}
