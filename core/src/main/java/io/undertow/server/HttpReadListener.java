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
import java.nio.channels.Channel;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.util.WorkerDispatcher;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<PushBackStreamChannel> {


    private static HttpCompletionHandler COMPLETION_HANDLER = new HttpCompletionHandler() {
        @Override
        public void handleComplete() {

        }
    };

    private final StreamSinkChannel responseChannel;

    private ParseState state = new ParseState();
    private HttpServerExchange httpServerExchange;

    private final HttpServerConnection connection;

    private int read = 0;
    private final int maxRequestSize;

    HttpReadListener(final StreamSinkChannel responseChannel, final PushBackStreamChannel requestChannel, final HttpServerConnection connection) {
        this.responseChannel = responseChannel;
        this.connection = connection;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        httpServerExchange = new HttpServerExchange(connection, requestChannel, this.responseChannel);
        httpServerExchange.addExchangeCompleteListener(new StartNextRequestAction(requestChannel, responseChannel));
        if(connection.getPipeLiningBuffer() != null) {
            httpServerExchange.addResponseWrapper(connection.getPipeLiningBuffer().getChannelWrapper());
        }
        httpServerExchange.addResponseWrapper(new ChannelWrapper<StreamSinkChannel>() {
            @Override
            public StreamSinkChannel wrap(final ChannelFactory<StreamSinkChannel> channelFactory, HttpServerExchange exchange) {
                return new HttpResponseChannel(channelFactory.create(), connection.getBufferPool(), exchange);
            }
        });

    }

    public void handleEvent(final PushBackStreamChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
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
                if (res == 0) {

                    //if we ever fail to read then we flush the pipeline buffer
                    //this relies on us always doing an eager read when starting a request,
                    //rather than waiting to be notified of data being available
                    final PipeLiningBuffer pipeLiningBuffer = connection.getPipeLiningBuffer();
                    if (pipeLiningBuffer != null && !pipeLiningBuffer.flushPipelinedData()) {
                            channel.suspendReads();
                            connection.getChannel().getWriteSetter().set(new ChannelListener<Channel>() {
                                @Override
                                public void handleEvent(Channel c) {
                                    try {
                                        if (pipeLiningBuffer.flushPipelinedData()) {
                                            connection.getChannel().getWriteSetter().set(null);
                                            connection.getChannel().suspendWrites();

                                            channel.getReadSetter().set(this);
                                            channel.resumeReads();
                                        }
                                    } catch (IOException e) {
                                        UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
                                        IoUtils.safeClose(connection.getChannel());
                                    }
                                }
                            });
                    } else if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                }
                if (res == -1) {
                    try {
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
                buffer.flip();
                int remaining = HttpParser.INSTANCE.handle(buffer, res, state, httpServerExchange);
                if (remaining > 0) {
                    free = false;
                    channel.unget(pooled);
                }
                int total = read + res - remaining;
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
            try {
                httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http"); //todo: determine if this is https
                state = null;
                this.httpServerExchange = null;
                connection.getRootHandler().handleRequest(httpServerExchange, COMPLETION_HANDLER);

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
    private static class StartNextRequestAction implements ExchangeCompleteListener {

        private PushBackStreamChannel requestChannel;
        private StreamSinkChannel responseChannel;


        public StartNextRequestAction(final PushBackStreamChannel requestChannel, final StreamSinkChannel responseChannel) {
            this.requestChannel = requestChannel;
            this.responseChannel = responseChannel;
        }

        @Override
        public void exchangeComplete(final HttpServerExchange exchange, final boolean isUpgrade) {
            if (exchange.isPersistent() && !exchange.isUpgrade()) {
                final PushBackStreamChannel channel = this.requestChannel;
                final HttpReadListener listener = new HttpReadListener(responseChannel, channel, exchange.getConnection());
                if (channel.isReadResumed()) {
                    channel.suspendReads();
                }
                WorkerDispatcher.dispatchNextRequest(channel, new DoNextRequestRead(listener, channel));
                responseChannel = null;
                this.requestChannel = null;
            }
        }

        private static class DoNextRequestRead implements Runnable {

            private final HttpReadListener listener;
            private final PushBackStreamChannel channel;

            public DoNextRequestRead(HttpReadListener listener, PushBackStreamChannel channel) {
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
