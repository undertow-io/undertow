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

package io.undertow.client.http2;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientProvider;
import io.undertow.protocols.http2.Http2Channel;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * HTTP2 client provider that connects to endpoints that are known to support HTTP2
 *
 * @author Stuart Douglas
 */
public class Http2PriorKnowledgeClientProvider implements ClientProvider {

    public static final byte[] PRI_REQUEST = {'P','R','I',' ','*',' ','H','T','T','P','/','2','.','0','\r','\n','\r','\n','S','M','\r','\n','\r','\n'};

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        connect(listener, null, uri, worker, ssl, bufferPool, options);
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        connect(listener, null, uri, ioThread, ssl, bufferPool, options);
    }

    @Override
    public Set<String> handlesSchemes() {
        return new HashSet<>(Arrays.asList(new String[]{"h2c-prior"}));
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {

        if (bindAddress == null) {
            worker.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
        } else {
            worker.openStreamConnection(bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), null, options).addNotifier(createNotifier(listener), null);
        }}

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {

        if (bindAddress == null) {
            ioThread.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
        } else {
            ioThread.openStreamConnection(bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), null, options).addNotifier(createNotifier(listener), null);
        }
    }

    private IoFuture.Notifier<StreamConnection, Object> createNotifier(final ClientCallback<ClientConnection> listener) {
        return new IoFuture.Notifier<StreamConnection, Object>() {
            @Override
            public void notify(IoFuture<? extends StreamConnection> ioFuture, Object o) {
                if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                    listener.failed(ioFuture.getException());
                }
            }
        };
    }

    private ChannelListener<StreamConnection> createOpenListener(final ClientCallback<ClientConnection> listener, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection connection) {
                handleConnected(connection, listener, bufferPool, options);
            }
        };
    }

    private void handleConnected(final StreamConnection connection, final ClientCallback<ClientConnection> listener, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        try {
            final ByteBuffer pri = ByteBuffer.wrap(PRI_REQUEST);
            pri.flip();
            ConduitStreamSinkChannel sink = connection.getSinkChannel();
            sink.write(pri);
            if(pri.hasRemaining()) {
                sink.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    @Override
                    public void handleEvent(ConduitStreamSinkChannel channel) {
                        try {
                            channel.write(pri);
                            if(pri.hasRemaining()) {
                                return;
                            }
                            listener.completed(new Http2ClientConnection(new Http2Channel(connection, null, bufferPool, null, true, false, options), false));
                        } catch (IOException e) {
                            listener.failed(e);
                        }
                    }
                });
                return;
            }
            listener.completed(new Http2ClientConnection(new Http2Channel(connection, null, bufferPool, null, true, false, options), false));
        } catch (IOException e) {
            listener.failed(e);
        }
    }
}
