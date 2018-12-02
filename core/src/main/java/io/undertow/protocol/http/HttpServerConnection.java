/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.protocol.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.UndertowOptionMap;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public class HttpServerConnection extends ServerConnection implements Closeable {

    final List<CloseListener> closeListeners = new CopyOnWriteArrayList<>();

    final ChannelHandlerContext ctx;

    volatile HttpServerExchange currentExchange;
    private boolean responseCommited;
    private boolean responseComplete;

    private final Executor executor;

    //TODO: remove this
    private final LinkedBlockingDeque<ByteBuf> contents = new LinkedBlockingDeque<>();
    private static final ByteBuf LAST = Unpooled.buffer(0);


    public HttpServerConnection(ChannelHandlerContext ctx, Executor executor) {
        this.ctx = ctx;
        this.executor = executor;
    }

    @Override
    public ByteBufAllocator getByteBufferPool() {
        return ctx.alloc();
    }

    /**
     * @return The connections worker
     */
    public Executor getWorker() {
        return executor;
    }

    /**
     * @return The IO thread associated with the connection
     */
    public EventExecutor getIoThread() {
        return ctx.executor();
    }

    /**
     * Sends an out of band response, such as a HTTP 100-continue response.
     * <p>
     * WARNING: do not attempt to write to the current exchange until the out of band
     * exchange has been fully written. Doing so may have unexpected results.
     * <p>
     * TODO: this needs more thought.
     *
     * @param exchange The current exchange
     * @return The out of band exchange.
     */
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        throw new RuntimeException("nyi");
    }

    /**
     * @return <code>true</code> if this connection supports sending a 100-continue response
     */
    public boolean isContinueResponseSupported() {
        return true;
    }

    @Override
    public void endExchange(HttpServerExchange exchange) {
        currentExchange = null;
    }

    /**
     * @return true if the connection is open
     */
    public boolean isOpen() {
        return ctx.channel().isOpen();
    }

    public void close() throws IOException {
        ctx.channel().close().syncUninterruptibly();
    }

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     *
     * @return The address of the remote peer
     */
    public SocketAddress getPeerAddress() {
        return ctx.channel().remoteAddress();
    }

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     *
     * @param type The type of address to return
     * @param <A>  The address type
     * @return The remote endpoint address
     */
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        SocketAddress addr = getPeerAddress();
        if (type.isAssignableFrom(addr.getClass())) {
            return (A) addr;
        }
        return null;
    }

    public SocketAddress getLocalAddress() {
        return ctx.channel().localAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {

        SocketAddress addr = getLocalAddress();
        if (type.isAssignableFrom(addr.getClass())) {
            return (A) addr;
        }
        return null;
    }

    @Override
    public UndertowOptionMap getUndertowOptions() {
        //TODO
        return UndertowOptionMap.EMPTY;
    }

    @Override
    public int getBufferSize() {
        throw new RuntimeException("NYI");
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return null;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {

    }

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    public synchronized void addCloseListener(CloseListener listener) {
        if (ctx.channel().isOpen()) {
            closeListeners.add(listener);
        } else {
            listener.closed(this);
        }
    }


    /**
     * @return true if this connection supports HTTP upgrade
     */
    protected boolean isUpgradeSupported() {
        return false;
    }

    /**
     * @return <code>true</code> if this connection supports the HTTP CONNECT verb
     */
    protected boolean isConnectSupported() {
        return false;
    }

    /**
     * Invoked when the exchange is complete.
     */
    protected void exchangeComplete(HttpServerExchange exchange) {
        contents.add(LAST);
        contents.poll();
    }

    /**
     * Callback that is invoked if the max entity size is updated.
     *
     * @param exchange The current exchange
     */
    protected void maxEntitySizeUpdated(HttpServerExchange exchange) {

    }

    /**
     * Returns a string representation describing the protocol used to transmit messages
     * on this connection.
     *
     * @return the transport protocol
     */
    public String getTransportProtocol() {
        return "HTTP/1.1";
    }

    public boolean isPushSupported() {
        return false;
    }

    public boolean isRequestTrailerFieldsSupported() {
        return false;
    }


    public ChannelFuture writeAsync(ByteBuf data, boolean last, HttpServerExchange exchange) {
        if(!exchange.isInIoThread() && Connectors.isRunningHandlerChain(exchange)) {
            //this should never happen, as the exchange should delay it
            //TODO: we need to delay the notification to keep with the Undertow thread model

        }
        return write(data, last, exchange);
    }
    public void writeBlocking(ByteBuf data, boolean last, HttpServerExchange exchange) throws IOException {
        ChannelFuture write = write(data, last, exchange);
        try {
            write.get();
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e);
        }
    }

    public ChannelFuture write(ByteBuf data, boolean last, HttpServerExchange exchange) {
        if (exchange != this.currentExchange || responseComplete) {
            DefaultChannelPromise defaultChannelPromise = new DefaultChannelPromise(ctx.channel());
            defaultChannelPromise.setFailure(new IOException("Exchange has completed"));
            return defaultChannelPromise;
        }
        if (last) {
            responseComplete = true;
            if (responseCommited) {
                return ctx.writeAndFlush(new DefaultLastHttpContent(data));
            } else {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(exchange.getStatusCode()), data == null ? Unpooled.EMPTY_BUFFER : data);
                for (HeaderValues i : exchange.getResponseHeaders()) {
                    response.headers().add(i.getHeaderName().toString(), i);
                }
                if(!response.headers().contains(Headers.CONTENT_LENGTH_STRING)) {
                    response.headers().add(Headers.CONTENT_LENGTH_STRING, data == null ? 0 : data.readableBytes());
                }
                return ctx.writeAndFlush(response);
            }
        }
        if (responseCommited) {
            return ctx.writeAndFlush(new DefaultHttpContent(data));
        } else {
            responseCommited = true;
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(exchange.getStatusCode()));
            for (HeaderValues i : exchange.getResponseHeaders()) {
                response.headers().add(i.getHeaderName().toString(), i.getFirst());
            }
            ctx.write(response);
            return ctx.writeAndFlush(new DefaultHttpContent(data));
        }

    }

    public ChannelPromise createPromise() {
        return ctx.newPromise();
    }

    @Override
    public void runResumeReadWrite() {

    }

    @Override
    protected boolean isReadDataAvailable() {
        return !contents.isEmpty();
    }

    /**
     * Reads some data from the exchange. Can only be called if {@link #isReadDataAvailable()} returns true.
     *
     * Returns null when all data is full read
     * @return
     * @throws IOException
     */
    @Override
    protected ByteBuf readAsync() throws IOException {
        ByteBuf buf = contents.pop();
        if(buf == LAST) {
            return null;
        }
        return buf;
    }

    @Override
    public ChannelFuture writeFileAsync(FileChannel file, long position, long count, HttpServerExchange exchange) {
        return null;
    }

    @Override
    public void writeFileBlocking(FileChannel file, long position, long count, HttpServerExchange exchange) throws IOException {

    }

    @Override
    public ByteBuf readBlocking() throws IOException {
        ByteBuf buf = null;
        try {
            buf = contents.takeFirst();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if(buf == LAST) {
            return null;
        }
        return buf;
    }

    public void setExchange(HttpServerExchange exchange) {
        responseCommited = false;
        responseComplete = false;
        this.currentExchange = exchange;
    }

    public void addData(HttpContent msg) {
        ByteBuf content = msg.content();
        content.retain();
        contents.add(content);
        if(msg instanceof LastHttpContent) {
            contents.add(LAST);
        }

    }
}
