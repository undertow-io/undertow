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

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

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
            try {
                ChannelListeners.initiateTransfer(response.readReplyBody(), exchange.getResponseChannel(), exchange.getConnection().getBufferPool());
            } catch (IOException e) {
                exchange.setResponseCode(500);
                exchange.endExchange();
            }
        }
    };

    private final HttpClient client;
    private final SocketAddress destination;
    private final IoFuture.HandlingNotifier<HttpClientConnection,HttpServerExchange> notifier = new IoFuture.HandlingNotifier<HttpClientConnection, HttpServerExchange>() {
        public void handleCancelled(final HttpServerExchange exchange) {
            exchange.setResponseCode(500);
            exchange.endExchange();
        }

        public void handleFailed(final IOException exception, final HttpServerExchange exchange) {
            exchange.setResponseCode(500);
            exchange.endExchange();
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
     * @param client the pre-configured HTTP client to use
     * @param destination the destination address to proxy traffic to
     */
    public ProxyHandler(final HttpClient client, final SocketAddress destination) {
        this.client = client;
        this.destination = destination;
    }

    public void handleRequest(final HttpServerExchange exchange) {
        final HttpServerConnection serverConnection = exchange.getConnection();
        HttpClientConnection clientConnection = serverConnection.getAttachment(proxyConnection);
        if (clientConnection == null) {
            client.connect(destination, OptionMap.EMPTY).addNotifier(notifier, exchange);
            return;
        }
        final HttpClientRequest request;
        try {
            request = clientConnection.createRequest(exchange.getRequestMethod(), new URI(exchange.getRequestURI()));
        } catch (URISyntaxException e) {
            exchange.setResponseCode(500);
            exchange.endExchange();
            return;
        }
        final HeaderMap inboundRequestHeaders = exchange.getRequestHeaders();
        final HeaderMap outboundRequestHeaders = request.getRequestHeaders();
        copyHeaders(outboundRequestHeaders, inboundRequestHeaders);
        final long requestContentLength = exchange.getRequestContentLength();
        if (requestContentLength == 0L) {
            request.writeRequestBody(0L);
        } else {
            ChannelListeners.initiateTransfer(exchange.getRequestChannel(), request.writeRequestBody(requestContentLength), serverConnection.getBufferPool());
        }
        final IoFuture<HttpClientResponse> futureResponse = request.getResponse();
        futureResponse.addNotifier(RESPONSE_NOTIFIER, exchange);
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
}
