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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.net.ssl.SSLSession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.concurrent.EventExecutor;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.UndertowOptionMap;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public abstract class ServerConnection extends AbstractAttachable {


    protected abstract ByteBuf allocateBuffer();
    protected abstract ByteBuf allocateBuffer(boolean direct);

    protected abstract ByteBuf allocateBuffer(boolean direct, int bufferSize);

    protected abstract ByteBuf allocateBuffer(int bufferSize);

    /**
     *
     * @return The connections worker
     */
    public abstract Executor getWorker();

    /**
     *
     * @return The IO thread associated with the connection
     */
    public abstract EventExecutor getIoThread();

    /**
     * Sends a 100-continue response if it is required
     */
    public abstract void sendContinueIfRequired();


    public abstract void writeBlocking(ByteBuf data, boolean last, HttpServerExchange exchange) throws IOException;

    public abstract <T> void writeAsync(ByteBuf data, boolean last, HttpServerExchange exchange, IoCallback<T> callback, T context);

    protected abstract boolean isIoOperationQueued();

    protected abstract <T> void scheduleIoCallback(IoCallback<T> callback, T context, HttpServerExchange exchange);

    /**
     *
     * @return <code>true</code> if this connection supports sending a 100-continue response
     */
    public abstract boolean isContinueResponseSupported();

    /**
     *
     * @return true if the connection is open
     */
    public abstract boolean isOpen();

    protected abstract void close(HttpServerExchange exchange);

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

    protected abstract boolean isExecutingHandlerChain();

    protected abstract void beginExecutingHandlerChain(HttpServerExchange exchange);

    protected abstract void endExecutingHandlerChain(HttpServerExchange exchange);

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

    public abstract UndertowOptionMap getUndertowOptions();

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
    public abstract void setSslSessionInfo(SSLSessionInfo sessionInfo, HttpServerExchange exchange);

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    public abstract void addCloseListener(CloseListener listener);

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
//
//    protected abstract void setUpgradeListener(HttpUpgradeListener upgradeListener);
//
//    protected abstract void setConnectListener(HttpUpgradeListener connectListener);

    /**
     * Reads some data from the exchange. Can only be called if {@link #isReadDataAvailable()} returns true.
     *
     * Returns null when all data is full read
     * @return
     * @throws IOException
     */
    protected abstract void readAsync(IoCallback<ByteBuf> callback, HttpServerExchange exchange);
    protected abstract ByteBuf readBlocking(HttpServerExchange exchange) throws IOException;
    protected abstract int readBytesAvailable(HttpServerExchange exchange);

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
    public boolean pushResource(final String path, final String method, final HttpHeaders requestHeaders) {
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
    public boolean pushResource(final String path, final String method, final HttpHeaders requestHeaders, HttpHandler handler) {
        return false;
    }

    public boolean isPushSupported() {
        return false;
    }

    public abstract boolean isRequestTrailerFieldsSupported();

    public abstract ChannelPromise createPromise();

    public abstract void runResumeReadWrite();

    public abstract <T> void writeFileAsync(RandomAccessFile file, long position, long count, HttpServerExchange exchange, IoCallback<T> context, T callback);
    public abstract void writeFileBlocking(RandomAccessFile file, long position, long count, HttpServerExchange exchange) throws IOException;


    protected  void setUpgradeListener(Consumer<ChannelHandlerContext> listener) {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    protected abstract void ungetRequestBytes(ByteBuf buffer, HttpServerExchange exchange);

    public abstract void discardRequest(HttpServerExchange exchange);

    public interface CloseListener {

        void closed(final ServerConnection connection);
    }
}
