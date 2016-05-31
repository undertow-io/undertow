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

package io.undertow.server.protocol.http;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.XnioByteBufferPool;
import io.undertow.util.ALPN;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;

/**
 * Open listener adaptor for ALPN connections
 *
 * Not a proper open listener as such, but more a mechanism for selecting between them.
 *
 * The implementation delegates between {@link JDK9AlpnOpenListener} and {@link JettyAlpnOpenListener}
 * based on the current JDK version.
 *
 * @author Stuart Douglas
 */
public class AlpnOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private final AlpnDelegateListener delegate;

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions, DelegateOpenListener httpListener) {
        this(bufferPool, undertowOptions, "http/1.1", httpListener);
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool,  OptionMap undertowOptions) {
        this(bufferPool, undertowOptions, null, null);
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this(new XnioByteBufferPool(bufferPool), undertowOptions, fallbackProtocol, fallbackListener);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, DelegateOpenListener httpListener) {
        this(bufferPool, undertowOptions, "http/1.1", httpListener);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool) {
        this(bufferPool, OptionMap.EMPTY, null, null);
    }
    public AlpnOpenListener(ByteBufferPool bufferPool,  OptionMap undertowOptions) {
        this(bufferPool, undertowOptions, null, null);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        if(ALPN.JDK_9_ALPN_METHODS != null) {
            delegate = new JDK9AlpnOpenListener(bufferPool, undertowOptions, fallbackProtocol, fallbackListener);
        } else if (JDK8HackAlpnOpenListener.ENABLED) {
            delegate = new JDK8HackAlpnOpenListener(bufferPool, undertowOptions, fallbackProtocol, fallbackListener);
        } else {
            delegate = new JettyAlpnOpenListener(bufferPool, undertowOptions, fallbackProtocol, fallbackListener);
        }
    }


    @Override
    public HttpHandler getRootHandler() {
        return delegate.getRootHandler();
    }

    @Override
    public void setRootHandler(HttpHandler rootHandler) {
        delegate.setRootHandler(rootHandler);
    }

    @Override
    public OptionMap getUndertowOptions() {
        return delegate.getUndertowOptions();
    }

    @Override
    public void setUndertowOptions(OptionMap undertowOptions) {
        delegate.setUndertowOptions(undertowOptions);
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return delegate.getBufferPool();
    }

    @Override
    public ConnectorStatistics getConnectorStatistics() {
        return delegate.getConnectorStatistics();
    }

    private static class ListenerEntry {
        DelegateOpenListener listener;
        int weight;

        ListenerEntry(DelegateOpenListener listener, int weight) {
            this.listener = listener;
            this.weight = weight;
        }
    }

    public AlpnOpenListener addProtocol(String name, DelegateOpenListener listener, int weight) {
        delegate.addProtocol(name, listener, weight);
        return this;
    }

    public void handleEvent(final StreamConnection channel) {
        delegate.handleEvent(channel);
    }

    interface AlpnDelegateListener extends OpenListener {

        void addProtocol(String name, DelegateOpenListener listener, int weight);
    }

}
