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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
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
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.UndertowOptionMap;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public class HttpServerConnection extends ServerConnection {

    private final List<CloseListener> closeListeners = new CopyOnWriteArrayList<>();

    private final ChannelHandlerContext ctx;

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
    private final LinkedBlockingDeque<QueuedCallback> queuedCallbacks = new LinkedBlockingDeque<>();
    private static final ByteBuf LAST = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(new byte[0]));
    private static final ByteBuf CLOSED = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(new byte[0]));
    private final SSLSessionInfo sslSessionInfo;
    private volatile IOException closedException;

    private Consumer<ChannelHandlerContext> upgradeListener;

    private IoCallback<?> queuedWriteCallback;

    private Object queuedContextObject;

    private boolean queuedWriteLast;

    private Future<? super Void> writeListenerFuture; //used to prevent recursion when invoking the write listener

    private final int bufferSize;
    private final boolean direct;

    /**
     * If this flag is set then the request is current running through a
     * handler chain.
     * <p>
     * This will be true most of the time, this only time this will return
     * false is when performing async operations outside the scope of a call to
     * {@link Connectors#executeRootHandler(HttpHandler, BufferAllocator)}
     * <p>
     * If this is true then when the call stack returns the exchange will either be dispatched,
     * or the exchange will be ended.
     */
    private volatile boolean inHandlerChain;
    private volatile boolean canInvokeIoCallback = false;

    private final Runnable runEventLoop = new Runnable() {
        @Override
        public void run() {
            runIoCallbackLoop();
        }
    };

    private final GenericFutureListener<Future<? super Void>> asyncWriteListener = new GenericFutureListener<Future<? super Void>>() {
        @Override
        public void operationComplete(Future<? super Void> f) throws Exception {
            writeListenerFuture = f;
            runIoCallbackLoop();
        }
    };

    private volatile IoCallback<ByteBuf> readCallback;

    public HttpServerConnection(ChannelHandlerContext ctx, Executor executor, SSLSessionInfo sslSessionInfo, int bufferSize, boolean direct) {
        this.ctx = ctx;
        this.executor = executor;
        this.sslSessionInfo = sslSessionInfo;
        this.bufferSize = bufferSize;
        this.direct = direct;
    }


    @Override
    protected void setUpgradeListener(Consumer<ChannelHandlerContext> listener) {
        upgradeListener = listener;
    }

    @Override
    protected void ungetRequestBytes(ByteBuf buffer, HttpServerExchange exchange) {
        contents.addFirst(buffer);
        if (currentExchange.isRequestComplete()) {
            contents.add(LAST);
        }
        if (readCallback != null && !Connectors.isRunningHandlerChain(currentExchange)) {
            runIoCallbackLoop();
        }
    }

    @Override
    public void discardRequest(HttpServerExchange exchange) {
        if (currentExchange != exchange) {
            return;
        }
        discardMode = true;
        if (!currentExchange.isResponseStarted()) {
            currentExchange.responseHeaders().set(HttpHeaderNames.CONNECTION, "close");
            currentExchange.setPersistent(false);
        }
        while (!contents.isEmpty()) {
            contents.poll().release();
        }

    }

    void runIoCallbackLoop() {
        if (!canInvokeIoCallback) {
            return;
        }
        if (!ctx.executor().inEventLoop()) {
            ctx.executor().execute(runEventLoop);
            return;
        }
        canInvokeIoCallback = false;
        boolean asyncReadPossible = !contents.isEmpty() && this.readCallback != null;
        try {
            while (writeListenerFuture != null || asyncReadPossible || !queuedCallbacks.isEmpty()) {
                Future<? super Void> future = writeListenerFuture;
                if (future != null) {
                    writeListenerFuture = null;
                    HttpServerExchange currentExchange = HttpServerConnection.this.currentExchange;
                    if (future.isSuccess()) {
                        IoCallback callback = queuedWriteCallback;
                        Object context = queuedContextObject;
                        queuedContextObject = null;
                        queuedWriteCallback = null;

                        if (queuedWriteLast) {
                            Connectors.terminateResponse(currentExchange);
                        }
                        callback.onComplete(currentExchange, context);
                    } else {
                        IoCallback callback = queuedWriteCallback;
                        Object context = queuedContextObject;
                        queuedContextObject = null;
                        queuedWriteCallback = null;
                        callback.onException(currentExchange, context, new IOException(future.cause()));
                    }
                }
                if (asyncReadPossible) {
                    HttpServerExchange exchange = HttpServerConnection.this.currentExchange;
                    IoCallback<ByteBuf> readCallback = HttpServerConnection.this.readCallback;
                    HttpServerConnection.this.readCallback = null;
                    if (readCallback != null) {
                        ByteBuf data = contents.poll();
                        if (data == LAST) {
                            Connectors.terminateRequest(exchange);
                            readCallback.onComplete(exchange, null);
                        } else if (data == CLOSED) {
                            readCallback.onException(exchange, null, new IOException(closedException));
                        } else if (data != null && data.readableBytes() > 0) {
                            readCallback.onComplete(exchange, data);
                        }
                    }
                }
                QueuedCallback c = queuedCallbacks.poll();
                while (c != null) {
                    c.callback.onComplete(currentExchange, c.context);
                    c = queuedCallbacks.poll();
                }
                asyncReadPossible = !contents.isEmpty() && this.readCallback != null;
            }
        } finally {
            canInvokeIoCallback = true;
        }
    }

    @Override
    protected ByteBuf allocateBuffer() {
        return allocateBuffer(direct);
    }
    @Override
    protected ByteBuf allocateBuffer(boolean direct) {
        if(direct) {
            return ctx.channel().alloc().directBuffer(bufferSize);
        } else {
            return ctx.channel().alloc().heapBuffer(bufferSize);
        }
    }

    @Override
    protected ByteBuf allocateBuffer(boolean direct, int bufferSize) {
        if(direct) {
            return ctx.channel().alloc().directBuffer(bufferSize);
        } else {
            return ctx.channel().alloc().heapBuffer(bufferSize);
        }
    }

    @Override
    protected ByteBuf allocateBuffer(int bufferSize) {
        if(direct) {
            return ctx.channel().alloc().directBuffer(bufferSize);
        } else {
            return ctx.channel().alloc().heapBuffer(bufferSize);
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

    protected void close(HttpServerExchange currentExchange) {
        if(this.currentExchange == currentExchange) {
            ctx.channel().close().syncUninterruptibly();
        }
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


    protected boolean isExecutingHandlerChain() {
        return inHandlerChain;
    }

    @Override
    protected void beginExecutingHandlerChain(HttpServerExchange exchange) {
        if(exchange != currentExchange) {
            return;
        }
        //TODO: can we just use one var for this?
        inHandlerChain = true;
        canInvokeIoCallback = false;
    }

    protected void endExecutingHandlerChain(HttpServerExchange exchange) {
        if(exchange != currentExchange) {
            return;
        }
        inHandlerChain = false;
        canInvokeIoCallback = true;

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
        return bufferSize;
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return sslSessionInfo;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo, HttpServerExchange exchange) {
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
        if (queuedWriteCallback != null) {
            //special case, where the buffer is null and we just want to end the exchange
            //TODO: this seems a bit less than ideal, but I don't see what else we can do
            if (data == null && last) {
                queuedWriteLast = true;
                return;
            }

            callback.onException(exchange, context, new IOException(UndertowMessages.MESSAGES.dataAlreadyQueued()));
            return;
        }
        queuedWriteCallback = callback;
        queuedContextObject = context;
        queuedWriteLast = last;
        //TODO: use a custom promise implementation for max efficency
        //TODO: this whole this needs some work
        ChannelFuture res = write(data, last, exchange, true);
        res.addListener(asyncWriteListener);

    }

    @Override
    protected boolean isIoOperationQueued() {
        return queuedWriteCallback != null || readCallback != null;
    }

    @Override
    protected <T> void scheduleIoCallback(IoCallback<T> callback, T context, HttpServerExchange exchange) {
        if(exchange != currentExchange) {
            callback.onException(exchange, context, new ClosedChannelException());
        }
        queuedCallbacks.add(new QueuedCallback(callback, context));
        runIoCallbackLoop();
    }

    public void writeBlocking(ByteBuf data, boolean last, HttpServerExchange exchange) throws IOException {

        ChannelFuture write = write(data, last, exchange, true);
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

    public ChannelFuture write(ByteBuf data, boolean last, HttpServerExchange exchange, boolean flush) {
        if (exchange != this.currentExchange || responseComplete) {
            if(data == null && last) {
                DefaultChannelPromise defaultChannelPromise = new DefaultChannelPromise(ctx.channel());
                defaultChannelPromise.setSuccess();
                return defaultChannelPromise;
            }
            DefaultChannelPromise defaultChannelPromise = new DefaultChannelPromise(ctx.channel());
            defaultChannelPromise.setFailure(UndertowMessages.MESSAGES.exchangeAlreadyComplete());
            return defaultChannelPromise;
        }
        if (last) {
            return writeLast(data, exchange);
        }
        if (responseCommited) {
            return ctx.writeAndFlush(new DefaultHttpContent(data));
        } else {
            responseCommited = true;
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(exchange.getStatusCode()), exchange.responseHeaders());
            if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
            }
            if (data == null && flush) {
                return ctx.writeAndFlush(response);
            } else if (data == null) {
                return ctx.write(response);
            } else {
                ctx.write(response);
                if (flush) {
                    return ctx.writeAndFlush(new DefaultHttpContent(data));
                } else {
                    return ctx.write(new DefaultHttpContent(data));
                }
            }

        }

    }

    private ChannelFuture writeLast(ByteBuf data, HttpServerExchange exchange) {
        responseComplete = true;
        HttpContent resp;
        if (responseCommited) {
            if (data == null) {
                resp = new DefaultLastHttpContent();
            } else {
                resp = new DefaultLastHttpContent(data);
            }
        } else {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(exchange.getStatusCode()), data == null ? Unpooled.EMPTY_BUFFER : data,exchange.responseHeaders(), EmptyHttpHeaders.INSTANCE);
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                response.headers().add(HttpHeaderNames.CONTENT_LENGTH, data == null ? 0 : data.readableBytes());
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

    public ChannelPromise createPromise() {
        return ctx.newPromise();
    }

    @Override
    public void runResumeReadWrite() {
        if (readCallback != null && !contents.isEmpty() || queuedWriteCallback != null) {
            runIoCallbackLoop();
        }
    }

    @Override
    protected void readAsync(IoCallback<ByteBuf> callback, HttpServerExchange exchange) {
        if(exchange != currentExchange) {
            callback.onException(exchange, null, new ClosedChannelException());
        }
        this.readCallback = callback;
        if (!Connectors.isRunningHandlerChain(exchange) && !contents.isEmpty()) {
            runIoCallbackLoop();
        }
    }

    @Override
    public <T> void writeFileAsync(RandomAccessFile file, long position, long count, HttpServerExchange exchange, IoCallback<T> callback, T context) {

        Objects.requireNonNull(callback);
        if (queuedWriteCallback != null) {
            callback.onException(exchange, context, new IOException(UndertowMessages.MESSAGES.dataAlreadyQueued()));
            return;
        }
        queuedWriteCallback = callback;
        queuedContextObject = context;
        queuedWriteLast = true;


        if (!responseCommited) {
            write(null, false, exchange, false);
        }
        if (ctx.pipeline().get(SslHandler.class) == null) {
            ctx.write(new DefaultFileRegion(file.getChannel(), position, count), ctx.newProgressivePromise());
            // Write the end marker.
            write(null, true, exchange, true)
                    .addListener(asyncWriteListener);
        } else {
            try {
                responseComplete = true;
                ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(file, position, count, 8192)),
                        ctx.newProgressivePromise()).addListener(asyncWriteListener);
            } catch (IOException e) {
                callback.onException(exchange, context, e);
            }
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
        }
    }

    @Override
    public void writeFileBlocking(RandomAccessFile file, long position, long count, HttpServerExchange exchange) throws IOException {
        if (!responseCommited) {
            write(null, false, exchange, false);
        }
        if (ctx.pipeline().get(SslHandler.class) == null) {
            ctx.write(new DefaultFileRegion(file.getChannel(), position, count), ctx.newProgressivePromise());
            // Write the end marker.
            try {
                write(null, true, exchange, true).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        } else {
            try {
                ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(file, position, count, 8192)),
                        ctx.newProgressivePromise()).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
        }
    }

    @Override
    public ByteBuf readBlocking(HttpServerExchange exchange) throws IOException {
        if(exchange != currentExchange) {
            throw new ClosedChannelException();
        }
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
        } else if (buf == CLOSED) {
            throw new IOException(closedException);
        }
        return buf;
    }

    @Override
    protected int readBytesAvailable(HttpServerExchange exchange) {
        if(exchange != currentExchange) {
            return -1;
        }
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
        if (readCallback != null && canInvokeIoCallback) {
            runIoCallbackLoop();
        }
    }

    public void closed(IOException e) {
        closedException = e;
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

    private static class QueuedCallback {
        final IoCallback callback;
        final Object context;

        private QueuedCallback(IoCallback callback, Object context) {
            this.callback = callback;
            this.context = context;
        }
    }
}
