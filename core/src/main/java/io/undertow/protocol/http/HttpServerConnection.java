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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.server.Connectors;
import io.undertow.server.HttpContinue;
import io.undertow.server.HttpHandler;
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

    private volatile HttpServerExchange currentExchange;
    private boolean responseCommited;
    private boolean responseComplete;
    /**
     * If the connection is going away after this request and we should just discard all data
     */
    private volatile boolean discardMode;

    private final Executor executor;

    //TODO: remove this
    private final LinkedBlockingDeque<ByteBuf> contents = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<QueuedExchange> queuedExchanges = new LinkedBlockingDeque<>();
    private static final ByteBuf LAST = Unpooled.buffer(0);
    private static final ByteBuf CLOSED = Unpooled.buffer(0);
    private final SSLSessionInfo sslSessionInfo;

    private Consumer<ChannelHandlerContext> upgradeListener;
    private ByteBuf queuedAsyncData;
    private IoCallback<?> queuedCalback;
    private Object queuedContextObject;
    private boolean queuedWriteLast;
    private boolean inWriteListenerInvocation;
    private Future<? super Void> writeListenerFuture; //used to prevent recursion when invoking the write listener
    private final GenericFutureListener<Future<? super Void>> asyncWriteListener = new GenericFutureListener<Future<? super Void>>() {
        @Override
        public void operationComplete(Future<? super Void> f) throws Exception {
            writeListenerFuture = f;
            if (inWriteListenerInvocation) {
                return;
            }
            inWriteListenerInvocation = true;
            try {
                while (writeListenerFuture != null) {
                    Future<? super Void> future = writeListenerFuture;
                    writeListenerFuture = null;
                    HttpServerExchange currentExchange = HttpServerConnection.this.currentExchange;
                    if (future.isSuccess()) {
                        IoCallback callback = queuedCalback;
                        Object context = queuedContextObject;
                        queuedAsyncData = null;
                        queuedContextObject = null;
                        queuedCalback = null;

                        if (queuedWriteLast) {
                            Connectors.terminateResponse(currentExchange);
                        }
                        callback.onComplete(currentExchange, context);
                        if (queuedCalback != null) {
                            write(queuedAsyncData, queuedWriteLast, currentExchange)
                                    .addListener(this);
                        }
                    } else {
                        IoCallback callback = queuedCalback;
                        Object context = queuedContextObject;
                        queuedAsyncData = null;
                        queuedContextObject = null;
                        queuedCalback = null;
                        callback.onException(currentExchange, context, new IOException(future.cause()));
                    }
                }
            } finally {
                inWriteListenerInvocation = false;
            }
        }
    };

    private volatile IoCallback<ByteBuf> readCallback;


    public HttpServerConnection(ChannelHandlerContext ctx, Executor executor, SSLSessionInfo sslSessionInfo) {
        this.ctx = ctx;
        this.executor = executor;
        this.sslSessionInfo = sslSessionInfo;
    }

    @Override
    public ByteBufAllocator getByteBufferPool() {
        return ctx.alloc();
    }


    @Override
    protected void setUpgradeListener(Consumer<ChannelHandlerContext> listener) {
        upgradeListener = listener;
    }

    @Override
    protected void ungetRequestBytes(ByteBuf buffer) {
        contents.addFirst(buffer);
        if (currentExchange.isRequestComplete()) {
            contents.add(LAST);
        }
        if (readCallback != null && !Connectors.isRunningHandlerChain(currentExchange)) {
            executeReadCallback();
        }
    }

    @Override
    public void discardRequest() {
        if (currentExchange == null) {
            return;
        }
        discardMode = true;
        if (!currentExchange.isResponseStarted()) {
            currentExchange.getResponseHeaders().put(Headers.CONNECTION, "close");
            currentExchange.setPersistent(false);
        }
        while (!contents.isEmpty()) {
            contents.poll().release();
        }

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

    @Override
    public void sendContinueIfRequired() {
        if (currentExchange.isResponseStarted()) {
            return;
        }
        if (HttpContinue.requiresContinueResponse(currentExchange)) {
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }
    }


    /**
     * @return <code>true</code> if this connection supports sending a 100-continue response
     */
    public boolean isContinueResponseSupported() {
        return true;
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
        return sslSessionInfo;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
        throw new RuntimeException("NYI");
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
        return true;
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
        this.currentExchange = null;
        if (!queuedExchanges.isEmpty()) {
            if (getIoThread().inEventLoop()) {
                QueuedExchange ex = queuedExchanges.poll();
                newExchange(ex.exchange, ex.handler);
            } else {
                getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (currentExchange == null) {
                            QueuedExchange ex = queuedExchanges.poll();
                            if (ex != null) {
                                newExchange(ex.exchange, ex.handler);
                            }

                        }
                    }
                });
            }
        }
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


    @Override
    public <T> void writeAsync(ByteBuf data, boolean last, HttpServerExchange exchange, IoCallback<T> callback, T context) {
        Objects.requireNonNull(callback);
        if (queuedAsyncData != null) {
            //special case, where the buffer is null and we just want to end the exchange
            //TODO: this seems a bit less than ideal, but I don't see what else we can do
            if (data == null && last) {
                queuedWriteLast = true;
                return;
            }

            callback.onException(exchange, context, new IOException(UndertowMessages.MESSAGES.dataAlreadyQueued()));
            return;
        }


        queuedAsyncData = data;
        queuedCalback = callback;
        queuedContextObject = context;
        queuedWriteLast = last;
        if (Connectors.isRunningHandlerChain(exchange) || inWriteListenerInvocation) {
            //delay, either till the handler chain returns or until the write listener invocation is done
            return;
        }
        //TODO: use a custom promise implementation for max efficency
        //TODO: this whole this needs some work
        ChannelFuture res = write(data, last, exchange);
        res.addListener(asyncWriteListener);

    }

    @Override
    protected boolean isIoOperationQueued() {
        return queuedAsyncData != null || readCallback != null;
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
        } finally {
            if (last) {
                Connectors.terminateResponse(exchange);
            }
        }
    }

    public ChannelFuture write(Object sendObj, boolean last, HttpServerExchange exchange) {
        if (exchange != this.currentExchange || responseComplete) {
            DefaultChannelPromise defaultChannelPromise = new DefaultChannelPromise(ctx.channel());
            defaultChannelPromise.setFailure(new IOException("Exchange has completed"));
            return defaultChannelPromise;
        }
        ByteBuf data = (ByteBuf) sendObj;
        if (last) {
            responseComplete = true;
            HttpContent resp;
            if (responseCommited) {
                if (data == null) {
                    resp = new DefaultLastHttpContent();
                } else {
                    resp = new DefaultLastHttpContent(data);
                }
            } else {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(exchange.getStatusCode()), data == null ? Unpooled.EMPTY_BUFFER : data);
                for (HeaderValues i : exchange.getResponseHeaders()) {
                    response.headers().add(i.getHeaderName().toString(), i);
                }
                if (!response.headers().contains(Headers.CONTENT_LENGTH_STRING)) {
                    response.headers().add(Headers.CONTENT_LENGTH_STRING, data == null ? 0 : data.readableBytes());
                }
                resp = response;

            }

            if (upgradeListener != null) {
                //we really need to avoid races, so as soon as the data is send we need to remove the HTTP handlers
                //and install the upgrade ones
                ChannelPromise promose = ctx.newPromise();
                promose.addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        ctx.pipeline().remove(HttpServerCodec.class);
                        ctx.pipeline().remove(NettyHttpServerHandler.class);
                        upgradeListener.accept(ctx);
                    }
                });
                getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        ctx.writeAndFlush(resp, promose);
                    }
                });
                return promose;
            } else {
                return ctx.writeAndFlush(resp);
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
            if (!response.headers().contains(Headers.CONTENT_LENGTH_STRING)) {
                response.headers().set(Headers.TRANSFER_ENCODING_STRING, "chunked");
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
        if (queuedAsyncData != null || queuedWriteLast) {
            write(queuedAsyncData, queuedWriteLast, currentExchange)
                    .addListener(asyncWriteListener);
        }
        if (readCallback != null && !contents.isEmpty()) {
            queueReadCallback();
        }
    }

    protected void queueReadCallback() {
        getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                executeReadCallback();
            }
        });
    }

    private void executeReadCallback() {
        HttpServerExchange exchange = HttpServerConnection.this.currentExchange;
        IoCallback<ByteBuf> readCallback = HttpServerConnection.this.readCallback;
        HttpServerConnection.this.readCallback = null;
        if (readCallback != null) {
            ByteBuf data = contents.poll();
            if (data == LAST) {
                Connectors.terminateRequest(exchange);
                readCallback.onComplete(exchange, null);
            } else if(data == CLOSED) {
                readCallback.onException(exchange, null, new ClosedChannelException());
            } else if (data != null && data.readableBytes() > 0) {
                readCallback.onComplete(exchange, data);
            }
        }

    }


    protected void readAsync(IoCallback<ByteBuf> callback) {
        HttpServerExchange exchange = this.currentExchange;
        this.readCallback = callback;
        if (!Connectors.isRunningHandlerChain(exchange) && !contents.isEmpty()) {
            queueReadCallback();
        }
    }

    @Override
    public <T> void writeFileAsync(FileChannel file, long position, long count, HttpServerExchange exchange, T context, IoCallback<T> callback) {
        DefaultFileRegion region = new DefaultFileRegion(file, position, count);
        callback.onException(exchange, null, new IOException("NOT IMPLEMENTED"));
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
        if (buf == LAST) {
            Connectors.terminateRequest(currentExchange);
            return null;
        } else if(buf == CLOSED) {
            throw new ClosedChannelException();
        }
        return buf;
    }

    @Override
    protected int readBytesAvailable() {
        int c = 0;
        for (ByteBuf i : contents) {
            c += i.readableBytes();
        }
        return c;
    }

    public void newExchange(HttpServerExchange exchange, HttpHandler rootHandler) {
        if (currentExchange != null) {
            queuedExchanges.add(new QueuedExchange(exchange, rootHandler));
            if (currentExchange == null) {
                QueuedExchange ex = queuedExchanges.poll();
                Connectors.executeRootHandler(ex.handler, ex.exchange);
            }
        } else {
            responseCommited = false;
            responseComplete = false;
            this.currentExchange = exchange;
            Connectors.executeRootHandler(rootHandler, exchange);
        }
    }

    public void addData(HttpContent msg) {
        if (discardMode) {
            msg.content().release();
            return;
        }
        ByteBuf content = msg.content();
        content.retain();
        if (content.readableBytes() > 0) {
            contents.add(content);
        }
        if (msg instanceof LastHttpContent) {
            contents.add(LAST);
        }
        if (readCallback != null && !Connectors.isRunningHandlerChain(currentExchange)) {
            executeReadCallback();
        }
    }

    public void closed() {
        if (discardMode) {
            return;
        }
        int count = 0;
        if (currentExchange != null) {
            count++;
        }
        count += queuedExchanges.size();
        for (int i = 0; i < count; ++i) {
            contents.add(CLOSED);
        }
    }

    private static class QueuedExchange {
        final HttpServerExchange exchange;
        final HttpHandler handler;

        private QueuedExchange(HttpServerExchange exchange, HttpHandler handler) {
            this.exchange = exchange;
            this.handler = handler;
        }
    }
}
