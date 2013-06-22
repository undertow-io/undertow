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

package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.IoUtils.safeClose;

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

    HttpReadListener(final HttpServerConnection connection, final HttpRequestParser parser) {
        this.connection = connection;
        this.parser = parser;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
    }

    public void newRequest() {
        state.reset();
        read = 0;
        httpServerExchange = new HttpServerExchange(connection);
        httpServerExchange.addExchangeCompleteListener(this);
    }

    public void handleEvent(final StreamSourceChannel channel) {

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
                        safeClose(connection);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }

                if (res == 0) {
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                } else if (res == -1) {
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
                        IoUtils.safeClose(channel);
                        return;
                    }
                    return;
                }
                //TODO: we need to handle parse errors
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
                } else {
                    int total = read + res;
                    read = total;
                    if (read > maxRequestSize) {
                        UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                        IoUtils.safeClose(connection);
                        return;
                    }
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
            HttpTransferEncoding.handleRequest(httpServerExchange, connection.getRootHandler());
        } catch (Exception e) {
            sendBadRequestAndClose(connection.getChannel(), e);
        } finally {
            if (free) pooled.free();
        }
    }

    private void sendBadRequestAndClose(final StreamConnection channel, final Exception exception) {
        UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(exception);
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
        if (exchange.isPersistent() && !exchange.isUpgrade()) {
            newRequest();
            StreamConnection channel = exchange.getConnection().getChannel();
            if (exchange.getConnection().getExtraBytes() == null) {
                //if we are not pipelining we just register a listener

                channel.getSourceChannel().getReadSetter().set(this);
                channel.getSourceChannel().resumeReads();
            } else {
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
        }
        nextListener.proceed();
    }

    @Override
    public void run() {
        handleEvent(connection.getChannel().getSourceChannel());
    }
}
