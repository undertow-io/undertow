/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<StreamSourceChannel> {

    private final StreamSinkChannel responseChannel;

    private ParseState state = new ParseState();
    private HttpServerExchange httpServerExchange;

    private final HttpServerConnection connection;

    private int read = 0;
    private final int maxRequestSize;

    HttpReadListener(final StreamSinkChannel responseChannel, final StreamSourceChannel requestChannel, final HttpServerConnection connection) {
        this.responseChannel = responseChannel;
        this.connection = connection;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        httpServerExchange = new HttpServerExchange(connection, requestChannel, this.responseChannel);
        httpServerExchange.addExchangeCompleteListener(new StartNextRequestAction(requestChannel, responseChannel));
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
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
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
                        final StreamSinkChannel responseChannel = this.responseChannel;
                        responseChannel.shutdownWrites();
                        // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                        if (!responseChannel.flush()) {
                            responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                            responseChannel.resumeWrites();
                        }
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                        }
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
                HttpParser.INSTANCE.handle(buffer, state, httpServerExchange);
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
            try {
                httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http"); //todo: determine if this is https
                state = null;
                this.httpServerExchange = null;
                this.httpServerExchange = null;
                HttpTransferEncoding.handleRequest(httpServerExchange, connection.getRootHandler());

            } catch (Throwable t) {
                //TODO: we should attempt to return a 500 status code in this situation
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                IoUtils.safeClose(channel);
                IoUtils.safeClose(connection);
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            IoUtils.safeClose(connection.getChannel());
        } finally {
            if (free) pooled.free();
        }
    }


    /**
     * Action that starts the next request
     */
    private static class StartNextRequestAction implements ExchangeCompletionListener {

        private StreamSourceChannel requestChannel;
        private StreamSinkChannel responseChannel;


        public StartNextRequestAction(final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel) {
            this.requestChannel = requestChannel;
            this.responseChannel = responseChannel;
        }

        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            if (exchange.isPersistent() && !exchange.isUpgrade()) {
                final StreamSourceChannel channel = this.requestChannel;
                final HttpReadListener listener = new HttpReadListener(responseChannel, channel, exchange.getConnection());
                if (exchange.getConnection().getExtraBytes() == null) {
                    //if we are not pipelining we just register a listener
                    channel.getReadSetter().set(listener);
                    channel.resumeReads();
                } else {
                    if(channel.isReadResumed()) {
                        channel.suspendReads();
                    }
                    if (exchange.isInIoThread()) {
                        channel.getIoThread().execute(new DoNextRequestRead(listener, channel));
                    } else {
                        Executor executor = exchange.getDispatchExecutor();
                        if(executor == null) {
                            executor = exchange.getConnection().getWorker();
                        }
                        executor.execute(new DoNextRequestRead(listener, channel));
                    }
                }
                responseChannel = null;
                this.requestChannel = null;
            }
            nextListener.proceed();
        }

        private static class DoNextRequestRead implements Runnable {

            private final HttpReadListener listener;
            private final StreamSourceChannel channel;

            public DoNextRequestRead(HttpReadListener listener, StreamSourceChannel channel) {
                this.listener = listener;
                this.channel = channel;
            }

            @Override
            public void run() {
                listener.handleEvent(channel);
            }
        }
    }
}
