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
import io.undertow.util.AbstractAttachable;

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLSession;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public abstract class ServerConnection extends AbstractAttachable implements ConnectedChannel  {

    private static final AtomicLong CONNECTION_ID_GENERATOR = new AtomicLong(0);
    private final long id = CONNECTION_ID_GENERATOR.incrementAndGet();

    public final long getId() {
        return this.id;
    }

    public String getProtocolRequestId() {
        return "";
    }

    /**
     *
     * @return The connections buffer pool
     */
    @Deprecated
    public abstract Pool<ByteBuffer> getBufferPool();

    /**
     *
     * @return The connections buffer pool
     */
    public abstract ByteBufferPool getByteBufferPool();

    /**
     *
     * @return The connections worker
     */
    public abstract XnioWorker getWorker();

    /**
     *
     * @return The IO thread associated with the connection
     */
    @Override
    public abstract XnioIoThread getIoThread();

    /**
     * Sends an out of band response, such as a HTTP 100-continue response.
     *
     * WARNING: do not attempt to write to the current exchange until the out of band
     * exchange has been fully written. Doing so may have unexpected results.
     *
     * TODO: this needs more thought.
     *
     * @return The out of band exchange.
     * @param exchange The current exchange
     */
    public abstract HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange);

    /**
     *
     * @return <code>true</code> if this connection supports sending a 100-continue response
     */
    public abstract boolean isContinueResponseSupported();

    /**
     * Invoked when the exchange is complete, and there is still data in the request channel. Some implementations
     * (such as SPDY and HTTP2) have more efficient ways to drain the request than simply reading all data
     * (e.g. RST_STREAM).
     *
     * After this method is invoked the stream will be drained normally.
     *
     * @param exchange           The current exchange.
     */
    public abstract void terminateRequestChannel(HttpServerExchange exchange);

    /**
     *
     * @return true if the connection is open
     */
    public abstract boolean isOpen();

    public abstract boolean supportsOption(Option<?> option);

    public abstract <T> T getOption(Option<T> option) throws IOException;

    public abstract <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;

    public abstract void close() throws IOException;

    /**
     *
     * Gets the SSLSession of the underlying connection, or null if SSL is not in use.
     *
     * Note that for client cert auth {@link #getSslSessionInfo()} should be used instead, as it
     * takes into account other information potentially provided by load balancers that terminate SSL
     *
     * @return The SSLSession of the connection
     */
    public SSLSession getSslSession() {
        return null;
    }

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     * @return The address of the remote peer
     */
    public abstract SocketAddress getPeerAddress();

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     *
     * @param type The type of address to return
     * @param <A> The address type
     * @return The remote endpoint address
     */
    public abstract <A extends SocketAddress> A getPeerAddress(Class<A> type);

    public abstract SocketAddress getLocalAddress();

    public abstract <A extends SocketAddress> A getLocalAddress(Class<A> type);

    public abstract OptionMap getUndertowOptions();

    public abstract int getBufferSize();

    /**
     * Gets SSL information about the connection. This could represent the actual
     * client connection, or could be providing SSL information that was provided
     * by a front end proxy.
     *
     * @return SSL information about the connection
     */
    public abstract SSLSessionInfo getSslSessionInfo();

    /**
     * Sets the current SSL information. This can be used by handlers to setup SSL
     * information that was provided by a front end proxy.
     *
     * If this is being set of a per request basis then you must ensure that it is either
     * cleared by an exchange completion listener at the end of the request, or is always
     * set for every request. Otherwise it is possible to SSL information to 'leak' between
     * requests.
     *
     * @param sessionInfo The ssl session information
     */
    public abstract void setSslSessionInfo(SSLSessionInfo sessionInfo);

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    public abstract void addCloseListener(CloseListener listener);

    /**
     * Upgrade the connection, if allowed
     * @return The StreamConnection that should be passed to the upgrade handler
     */
    protected abstract StreamConnection upgradeChannel();

    protected abstract ConduitStreamSinkChannel getSinkChannel();

    protected abstract ConduitStreamSourceChannel getSourceChannel();

    /**
     * Gets the sink conduit that should be used for this request.
     *
     * This allows the connection to apply any per-request conduit wrapping
     * that is required, without adding to the response wrappers array.
     *
     * There is no corresponding method for source conduits, as in general
     * conduits can be directly inserted into the connection after the
     * request has been read.
     *
     * @return The source conduit
     */
    protected abstract StreamSinkConduit getSinkConduit(HttpServerExchange exchange, final StreamSinkConduit conduit);

    /**
     *
     * @return true if this connection supports HTTP upgrade
     */
    protected abstract boolean isUpgradeSupported();

    /**
     *
     * @return <code>true</code> if this connection supports the HTTP CONNECT verb
     */
    protected abstract boolean isConnectSupported();

    /**
     * Invoked when the exchange is complete.
     */
    protected abstract void exchangeComplete(HttpServerExchange exchange);

    protected abstract void setUpgradeListener(HttpUpgradeListener upgradeListener);

    protected abstract void setConnectListener(HttpUpgradeListener connectListener);

    /**
     * Callback that is invoked if the max entity size is updated.
     *
     * @param exchange The current exchange
     */
    protected abstract void maxEntitySizeUpdated(HttpServerExchange exchange);

    /**
     * Returns a string representation describing the protocol used to transmit messages
     * on this connection.
     *
     * @return the transport protocol
     */
    public abstract String getTransportProtocol();

    /**
     * Attempts to push a resource if this connection supports server push. Otherwise the request is ignored.
     *
     * Note that push is always done on a best effort basis, even if this method returns true it is possible that
     * the remote endpoint will reset the stream
     *
     *
     * @param path The path of the resource
     * @param method The request method
     * @param requestHeaders The request headers
     * @return <code>true</code> if the server attempted the push, false otherwise
     */
    public boolean pushResource(final String path, final HttpString method, final HeaderMap requestHeaders) {
        return false;
    }

    /**
     * Attempts to push a resource if this connection supports server push. Otherwise the request is ignored.
     *
     * Note that push is always done on a best effort basis, even if this method returns true it is possible that
     * the remote endpoint will reset the stream.
     *
     * The {@link io.undertow.server.HttpHandler} passed in will be used to generate the pushed response
     *
     *
     * @param path The path of the resource
     * @param method The request method
     * @param requestHeaders The request headers
     * @return <code>true</code> if the server attempted the push, false otherwise
     */
    public boolean pushResource(final String path, final HttpString method, final HeaderMap requestHeaders, HttpHandler handler) {
        return false;
    }

    public boolean isPushSupported() {
        return false;
    }

    public abstract boolean isRequestTrailerFieldsSupported();

    public interface CloseListener {

        void closed(final ServerConnection connection);
    }
}
