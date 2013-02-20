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

package io.undertow.client;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Emanuel Muckenhuber
 */
public class HttpProxyHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger("io.undertow.proxy.handler");
    private static final String rewrite = "rewrite";
    private static final String real = "real";

    private final SocketAddress address = new InetSocketAddress(7777);

    private final XnioWorker worker;
    private final OptionMap options;
    private final HttpClient client;
    private final ConcurrentMap<HttpServerConnection, IoFuture<HttpClientConnection>> connections = new ConcurrentHashMap<HttpServerConnection, IoFuture<HttpClientConnection>>();

    public HttpProxyHandler(final XnioWorker worker, final OptionMap options) {
        this.worker = worker;
        this.options = options;
        this.client = HttpClient.create(worker, options);
    }

    static String rewritePath(String path) {
        return path.replace(rewrite, real);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        try {
            IoFuture<HttpClientConnection> futureConnection = connections.get(exchange.getConnection());
            if(futureConnection == null) {
                futureConnection = client.connect(address, options);
                connections.put(exchange.getConnection(), futureConnection);
            }
            HttpClientUtils.addCallback(futureConnection, new HttpClientCallback<HttpClientConnection>() {
                @Override
                public void completed(final HttpClientConnection connection) {
                    try {
                        // Forward the request
                        final String protocol = exchange.getRequestMethod().toString();
                        final URI target = new URI(rewritePath(exchange.getRequestURI()));
                        final HttpClientRequest request = connection.sendRequest(protocol, target);
                        // Create the proxy callback
                        final Pool<ByteBuffer> pool = connection.getBufferPool();
                        final HttpClientCallback<HttpClientResponse> callback = createProxyCallback(exchange, pool);
                        // Handle POST requests
                        if(exchange.getRequestMethod().equals(Methods.POST)) {

                            final long contentLength = Long.parseLong(exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH));
                            final StreamSinkChannel sink = request.writeRequestBody(contentLength, callback);
                            final StreamSourceChannel source = exchange.getRequestChannel();
                            // Transfer the body streams
                            TempChannelListeners.initiateTransfer(Long.MAX_VALUE, source, sink, CLOSE_LISTENER, flushingCloseListener, CLOSING_EXCEPTION_LISTENER, CLOSING_EXCEPTION_LISTENER, pool);
                        } else {
                            // Other requests
                            request.writeRequest(callback);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        exchange.setResponseCode(500);
                        exchange.setPersistent(false);
                        exchange.endExchange();
                    }
                }

                @Override
                public void failed(IOException e) {
                    e.printStackTrace();
                    exchange.setResponseCode(500);
                    exchange.setPersistent(false);
                    exchange.endExchange();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            exchange.setResponseCode(500);
            exchange.setPersistent(false);
            exchange.endExchange();
        }
    }

    /**
     * Handle the http response for the proxied request.
     *
     * @param result the http response
     * @param exchange the server exchange
     * @throws IOException
     */
    void handleProxyResult(final HttpClientResponse result, final HttpServerExchange exchange, final Pool<ByteBuffer> bufferPool) throws IOException {
        final long contentLength = result.getContentLength();
        if(contentLength >= 0L) {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, contentLength);
        }

        final ChannelListener<StreamSinkChannel> requestFinished = new ChannelListener<StreamSinkChannel>() {
            @Override
            public void handleEvent(StreamSinkChannel channel) {
                exchange.endExchange();
            }
        };

        // Transfer body stream
        final StreamSourceChannel source = result.readReplyBody();
        final StreamSinkChannel sink = exchange.getResponseChannel();
        TempChannelListeners.initiateTransfer(Long.MAX_VALUE, source, sink, CLOSE_LISTENER, requestFinished, CLOSING_EXCEPTION_LISTENER, CLOSING_EXCEPTION_LISTENER, bufferPool);
    }

    /**
     * Create a request completion callback to handle the proxied result.
     *
     * @param exchange the http server exchange
     * @return the callback
     */
    HttpClientCallback<HttpClientResponse> createProxyCallback(final HttpServerExchange exchange, final Pool<ByteBuffer> bufferPool) {
        return new HttpClientCallback<HttpClientResponse>() {
            @Override
            public void completed(final HttpClientResponse result) {
                try {
                    handleProxyResult(result, exchange, bufferPool);
                } catch (IOException e) {
                    failed(e);
                }
            }

            @Override
            public void failed(final IOException e) {
                e.printStackTrace();
                exchange.setResponseCode(500);
                exchange.setPersistent(false);
                exchange.endExchange();
            }
        };
    }

    private static final ChannelListener<StreamSinkChannel> flushingCloseListener = HttpClientUtils.flushingChannelCloseListener();
    private static final ChannelListener<Channel> CLOSE_LISTENER = ChannelListeners.closingChannelListener();
    private static final ChannelExceptionHandler<Channel> CLOSING_EXCEPTION_LISTENER = new ChannelExceptionHandler<Channel>() {
        @Override
        public void handleException(Channel channel, IOException exception) {
            exception.printStackTrace();
            CLOSE_LISTENER.handleEvent(channel);
        }
    };


}
