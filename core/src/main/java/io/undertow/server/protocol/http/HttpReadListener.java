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

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.Connectors;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<StreamSourceChannel>, ExchangeCompletionListener, Runnable {

    private static final String BAD_REQUEST = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    private final HttpServerConnection connection;
    private final ParseState state = new ParseState();
    private final HttpRequestParser parser;

    private HttpServerExchange httpServerExchange;

    private int read = 0;
    private final int maxRequestSize;
    private final long maxEntitySize;

    HttpReadListener(final HttpServerConnection connection, final HttpRequestParser parser) {
        this.connection = connection;
        this.parser = parser;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        this.maxEntitySize = connection.getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, 0);
    }

    public void newRequest() {
        state.reset();
        read = 0;
        httpServerExchange = new HttpServerExchange(connection, maxEntitySize);
        httpServerExchange.addExchangeCompleteListener(this);
    }

    public void handleEvent(final StreamSourceChannel channel) {
        if(httpServerExchange == null) {
            //spurious wakeup, a request is in progress (or about to finish)
            //and the next request has arrived. We just suspend in this case
            //because resume always comes from the IO thread there is no chance of a race
            channel.suspendReads();
            return;
        }

        Pooled<ByteBuffer> existing = connection.getExtraBytes();

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.debug("Error reading request", e);
                        IoUtils.safeClose(connection);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }

                if (res <= 0) {
                    handleFailedRead(channel, res);
                    return;
                }
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                parser.handle(buffer, state, httpServerExchange);
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                int total = read + res;
                read = total;
                if (read > maxRequestSize) {
                    UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                    IoUtils.safeClose(connection);
                    return;
                }
            } while (!state.isComplete());

            // we remove ourselves as the read listener from the channel;
            // if the http handler doesn't set any then reads will suspend, which is the right thing to do
            channel.getReadSetter().set(null);
            channel.suspendReads();

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            httpServerExchange.putAttachment(UndertowOptions.ATTACHMENT_KEY, connection.getUndertowOptions());
            httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http");
            this.httpServerExchange = null;
            HttpTransferEncoding.setupRequest(httpServerExchange);
            Connectors.executeRootHandler(connection.getRootHandler(), httpServerExchange);
        } catch (Exception e) {
            sendBadRequestAndClose(connection.getChannel(), e);
            return;
        } finally {
            if (free) pooled.free();
        }
    }

    private void handleFailedRead(StreamSourceChannel channel, int res) {
        if (res == 0) {
            if (!channel.isReadResumed()) {
                channel.getReadSetter().set(this);
                channel.resumeReads();
            }
        } else if (res == -1) {
            handleConnectionClose(channel);
        }
    }

    private void handleConnectionClose(StreamSourceChannel channel) {
        try {
            channel.suspendReads();
            channel.shutdownReads();
            final StreamSinkChannel responseChannel = this.connection.getChannel().getSinkChannel();
            responseChannel.shutdownWrites();
            // will return false if there's a response queued ahead of this one, so we'll set up a listener then
            if (!responseChannel.flush()) {
                responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                responseChannel.resumeWrites();
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.debug("Error reading request", e);
            // fuck it, it's all ruined
            IoUtils.safeClose(connection);
            return;
        }
        return;
    }

    private void sendBadRequestAndClose(final StreamConnection channel, final Exception exception) {
        UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(exception);
        channel.getSourceChannel().suspendReads();
        new StringWriteChannelListener(BAD_REQUEST) {
            @Override
            protected void writeDone(final StreamSinkChannel c) {
                IoUtils.safeClose(channel);
            }
        }.setup(channel.getSinkChannel());
    }


    @Override
    public void exchangeEvent(final HttpServerExchange exchange, final ExchangeCompletionListener.NextListener nextListener) {
        connection.resetChannel();
        final HttpServerConnection connection = this.connection;
        if (exchange.isPersistent() && !exchange.isUpgrade()) {
            final StreamConnection channel = connection.getChannel();
            if (connection.getExtraBytes() == null) {
                //if we are not pipelining we just register a listener
                //we have to resume from with the io thread
                if (Thread.currentThread() != channel.getIoThread()) {
                    channel.getIoThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            newRequest();
                            channel.getSourceChannel().getReadSetter().set(HttpReadListener.this);
                            channel.getSourceChannel().resumeReads();
                        }
                    });
                } else {
                    newRequest();
                    channel.getSourceChannel().getReadSetter().set(this);
                    channel.getSourceChannel().resumeReads();
                }
            } else {
                newRequest();
                if (channel.getSourceChannel().isReadResumed()) {
                    channel.getSourceChannel().suspendReads();
                }
                if (exchange.isInIoThread()) {
                    channel.getIoThread().execute(this);
                } else {
                    Executor executor = exchange.getDispatchExecutor();
                    if (executor == null) {
                        executor = exchange.getConnection().getWorker();
                    }
                    executor.execute(this);
                }
            }
        } else if (!exchange.isPersistent()) {
            IoUtils.safeClose(connection);
        }
        nextListener.proceed();
    }

    @Override
    public void run() {
        handleEvent(connection.getChannel().getSourceChannel());
    }
}
