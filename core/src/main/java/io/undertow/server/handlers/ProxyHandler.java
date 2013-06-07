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

package io.undertow.server.handlers;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import io.undertow.UndertowLogger;
import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.client.HttpContinueNotification;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * An HTTP handler which proxies content to a remote server.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProxyHandler implements HttpHandler {
    private static final AttachmentKey<HttpClientConnection> proxyConnection = AttachmentKey.create(HttpClientConnection.class);
    private static final ChannelListener<HttpServerConnection> CONN_CLOSE_LISTENER = new ChannelListener<HttpServerConnection>() {
        public void handleEvent(final HttpServerConnection channel) {
            safeClose(channel.getAttachment(proxyConnection));
        }
    };
    private static final IoFuture.HandlingNotifier<HttpClientResponse, HttpServerExchange> RESPONSE_NOTIFIER = new IoFuture.HandlingNotifier<HttpClientResponse, HttpServerExchange>() {
        public void handleCancelled(final HttpServerExchange exchange) {
            exchange.setResponseCode(500);
            exchange.endExchange();
        }

        public void handleFailed(final IOException exception, final HttpServerExchange exchange) {
            exchange.setResponseCode(500);
            exchange.endExchange();
        }

        public void handleDone(final HttpClientResponse response, final HttpServerExchange exchange) {
            final HeaderMap inboundResponseHeaders = response.getResponseHeaders();
            final HeaderMap outboundResponseHeaders = exchange.getResponseHeaders();
            exchange.setResponseCode(response.getResponseCode());
            copyHeaders(outboundResponseHeaders, inboundResponseHeaders);

            if (exchange.isUpgrade()) {
                exchange.upgradeChannel(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                        ConnectedStreamChannel clientChannel = null;
                        try {
                            clientChannel = response.getRequest().getConnection().performUpgrade();
                            exchange.getConnection().resetChannel();

                            StreamConnection streamConnection = exchange.getConnection().getChannel();
                            if(exchange.getConnection().getExtraBytes() != null) {
                                streamConnection.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(streamConnection.getSourceChannel().getConduit(), exchange.getConnection()));
                            }
                            ChannelListeners.initiateTransfer(Long.MAX_VALUE, clientChannel, streamConnection.getSinkChannel(), ChannelListeners.closingChannelListener(), ChannelListeners.<StreamSinkChannel>writeShutdownChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), exchange.getConnection().getBufferPool());
                            ChannelListeners.initiateTransfer(Long.MAX_VALUE, streamConnection.getSourceChannel(), clientChannel, ChannelListeners.closingChannelListener(), ChannelListeners.<StreamSinkChannel>writeShutdownChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), exchange.getConnection().getBufferPool());

                        } catch (IOException e) {
                            IoUtils.safeClose();
                        }
                    }
                });
            }
            try {
                ChannelListeners.initiateTransfer(Long.MAX_VALUE, response.readReplyBody(), exchange.getResponseChannel(), ChannelListeners.closingChannelListener(), new HTTPTrailerChannelListener(response.getRequest(), exchange), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), exchange.getConnection().getBufferPool());
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                try {
                    if (!exchange.isResponseStarted()) {
                        exchange.setResponseCode(500);
                    }
                } finally {
                    exchange.endExchange();
                }
            }
        }
    };

    private final HttpClient client;
    private final SocketAddress destination;
    private final IoFuture.HandlingNotifier<HttpClientConnection, HttpServerExchange> notifier = new IoFuture.HandlingNotifier<HttpClientConnection, HttpServerExchange>() {
        public void handleCancelled(final HttpServerExchange exchange) {
            try {
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
            } finally {
                exchange.endExchange();
            }
        }

        public void handleFailed(final IOException exception, final HttpServerExchange exchange) {
            try {
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
            } finally {
                exchange.endExchange();
            }
        }

        public void handleDone(final HttpClientConnection connection, final HttpServerExchange exchange) {
            final HttpServerConnection serverConnection = exchange.getConnection();
            serverConnection.putAttachment(proxyConnection, connection);
            serverConnection.getCloseSetter().set(CONN_CLOSE_LISTENER);
            ProxyHandler.this.handleRequest(exchange);
        }
    };

    /**
     * Construct a new instance.
     *
     * @param client      the pre-configured HTTP client to use
     * @param destination the destination address to proxy traffic to
     */
    public ProxyHandler(final HttpClient client, final SocketAddress destination) {
        this.client = client;
        this.destination = destination;
    }

    public void handleRequest(final HttpServerExchange exchange) {
        final HttpServerConnection serverConnection = exchange.getConnection();
        final HttpClientConnection clientConnection = serverConnection.getAttachment(proxyConnection);
        if (clientConnection == null) {
            exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
                @Override
                public void run() {
                    client.connect(exchange.getIoThread(), destination, OptionMap.EMPTY).addNotifier(notifier, exchange);
                }
            });
            return;
        }
        exchange.dispatch(SameThreadExecutor.INSTANCE, new ProxyAction(clientConnection, exchange, serverConnection));
    }

    static void copyHeaders(final HeaderMap to, final HeaderMap from) {
        long f = from.fastIterateNonEmpty();
        HeaderValues values;
        while (f != -1L) {
            values = from.fiCurrent(f);
            to.putAll(values.getHeaderName(), values);
            f = from.fiNextNonEmpty(f);
        }
    }


    private static class ProxyAction implements Runnable {
        private final HttpClientConnection clientConnection;
        private final HttpServerExchange exchange;
        private final HttpServerConnection serverConnection;

        public ProxyAction(final HttpClientConnection clientConnection, final HttpServerExchange exchange, final HttpServerConnection serverConnection) {
            this.clientConnection = clientConnection;
            this.exchange = exchange;
            this.serverConnection = serverConnection;
        }

        @Override
        public void run() {
            final HttpClientRequest request;
            try {
                String requestURI = exchange.getRequestURI();
                String qs = exchange.getQueryString();
                if (qs != null && !qs.isEmpty()) {
                    requestURI += "?" + qs;
                }
                request = clientConnection.createRequest(exchange.getRequestMethod(), new URI(requestURI));
            } catch (URISyntaxException e) {
                exchange.setResponseCode(500);
                exchange.endExchange();
                return;
            }
            final HeaderMap inboundRequestHeaders = exchange.getRequestHeaders();
            final HeaderMap outboundRequestHeaders = request.getRequestHeaders();
            copyHeaders(outboundRequestHeaders, inboundRequestHeaders);
            final long requestContentLength = exchange.getRequestContentLength();

            if(HttpContinue.requiresContinueResponse(exchange)) {
                request.setContinueHandler(new HttpContinueNotification() {
                    @Override
                    public void handleContinue(final ContinueContext context) {
                        HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                            @Override
                            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                                context.done();
                            }

                            @Override
                            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                                context.done();
                            }
                        });
                    }
                });
            }



            if (requestContentLength == 0L || exchange.getRequestMethod().equals(Methods.GET)) {
                request.writeRequestBody(0L);
            } else {
                ChannelListeners.initiateTransfer(Long.MAX_VALUE, exchange.getRequestChannel(), request.writeRequestBody(requestContentLength), ChannelListeners.closingChannelListener(), new HTTPTrailerChannelListener(exchange, request), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), exchange.getConnection().getBufferPool());
            }
            final IoFuture<HttpClientResponse> futureResponse = request.getResponse();
            futureResponse.addNotifier(RESPONSE_NOTIFIER, exchange);
        }
    }

    private static final class HTTPTrailerChannelListener implements ChannelListener<StreamSinkChannel> {

        private final Attachable source;
        private final Attachable target;

        private HTTPTrailerChannelListener(final Attachable source, final Attachable target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public void handleEvent(final StreamSinkChannel channel) {
            HeaderMap trailers = source.getAttachment(ChunkedStreamSourceConduit.TRAILERS);
            if(trailers != null) {
                target.putAttachment(ChunkedStreamSinkConduit.TRAILERS, trailers);
            }
            try {
                channel.shutdownWrites();
                if(!channel.flush()) {
                    channel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, ChannelListeners.closingChannelExceptionHandler()));
                    channel.resumeWrites();
                }
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(channel);
            }

        }
    }
}
