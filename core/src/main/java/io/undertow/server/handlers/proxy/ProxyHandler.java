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

package io.undertow.server.handlers.proxy;

import io.undertow.UndertowLogger;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ContinueNotification;
import io.undertow.client.ProxiedRequestAttachments;
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
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Certificates;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.SameThreadExecutor;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP handler which proxies content to a remote server.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProxyHandler implements HttpHandler {

    private final ProxyClientProvider clientProvider;
    private final int maxRequestTime;

    private static final AttachmentKey<HttpServerExchange> EXCHANGE = AttachmentKey.create(HttpServerExchange.class);
    private static final AttachmentKey<XnioExecutor.Key> TIMEOUT_KEY = AttachmentKey.create(XnioExecutor.Key.class);


    private final ProxyClientHandler proxyClientHandler = new ProxyClientHandler();
    private final ProxyClientProviderHandler proxyClientProviderHandler = new ProxyClientProviderHandler();

    /**
     * Map of additional headers to add to the request.
     */
    private final Map<HttpString, ExchangeAttribute> requestHeaders = new CopyOnWriteMap<HttpString, ExchangeAttribute>();

    public ProxyHandler(ProxyClientProvider clientProvider, int maxRequestTime) {
        this.clientProvider = clientProvider;
        this.maxRequestTime = maxRequestTime;
    }


    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (maxRequestTime > 0) {
            final XnioExecutor.Key key = exchange.getIoThread().executeAfter(new Runnable() {
                @Override
                public void run() {
                    UndertowLogger.REQUEST_LOGGER.proxyRequestTimedOut(exchange.getRequestURI());
                    IoUtils.safeClose(exchange.getConnection());
                    ClientConnection clientConnection = exchange.getAttachment(ProxyClient.CONNECTION);
                    IoUtils.safeClose(clientConnection);
                }
            }, maxRequestTime, TimeUnit.MILLISECONDS);
            exchange.putAttachment(TIMEOUT_KEY, key);
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                    key.remove();
                    nextListener.proceed();
                }
            });
        }
        clientProvider.createProxyClient(exchange, proxyClientProviderHandler, -1, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a request header to the outgoing request. If the header resolves to null or an empty string
     * it will not be added, however any existing header with the same name will be removed.
     *
     * @param header    The header name
     * @param attribute The header value attribute.
     * @return this
     */
    public ProxyHandler addRequestHeader(final HttpString header, final ExchangeAttribute attribute) {
        requestHeaders.put(header, attribute);
        return this;
    }

    /**
     * Adds a request header to the outgoing request. If the header resolves to null or an empty string
     * it will not be added, however any existing header with the same name will be removed.
     * <p/>
     * The attribute value will be parsed, and the resulting exchange attribute will be used to create the actual header
     * value.
     *
     * @param header    The header name
     * @param attribute The header value attribute.
     * @return this
     */
    public ProxyHandler addRequestHeader(final HttpString header, final String attribute, final ClassLoader classLoader) {
        requestHeaders.put(header, ExchangeAttributes.parser(classLoader).parse(attribute));
        return this;
    }

    /**
     * Removes a request header
     *
     * @param header the header
     * @return this
     */
    public ProxyHandler removeRequestHeader(final HttpString header) {
        requestHeaders.remove(header);
        return this;
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

    private final class ProxyClientProviderHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            final ProxyClient proxyClient = exchange.getAttachment(ProxyClientProvider.CLIENT);
            if (proxyClient != null) {
                proxyClient.getConnection(exchange, proxyClientHandler, -1, TimeUnit.MILLISECONDS);
                return;
            }

            Throwable error = exchange.getAttachment(ProxyClientProvider.THROWABLE);
            if (error != null) {
                if (error instanceof Exception) {
                    throw (Exception) error;
                } else {
                    throw new RuntimeException(error);
                }
            }
            exchange.setResponseCode(500); //should not happen
        }
    }

    private final class ProxyClientHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            final ServerConnection serverConnection = exchange.getConnection();
            ClientConnection clientConnection = exchange.getAttachment(ProxyClient.CONNECTION);
            //see if we already have a client
            if (clientConnection != null) {
                exchange.dispatch(SameThreadExecutor.INSTANCE, new ProxyAction(clientConnection, exchange, requestHeaders));
                return;
            }

            //see if an error occurred
            Throwable error = serverConnection.getAttachment(ProxyClient.THROWABLE);
            if (error != null) {
                if (error instanceof Exception) {
                    throw (Exception) error;
                } else {
                    throw new RuntimeException(error);
                }
            }
            exchange.setResponseCode(500); //should not happen
        }
    }

    private static class ProxyAction implements Runnable {
        private final ClientConnection clientConnection;
        private final HttpServerExchange exchange;
        private final Map<HttpString, ExchangeAttribute> requestHeaders;

        public ProxyAction(final ClientConnection clientConnection, final HttpServerExchange exchange, Map<HttpString, ExchangeAttribute> requestHeaders) {
            this.clientConnection = clientConnection;
            this.exchange = exchange;
            this.requestHeaders = requestHeaders;
        }

        @Override
        public void run() {
            final ClientRequest request = new ClientRequest();
            String requestURI = exchange.getRequestURI();
            String qs = exchange.getQueryString();
            if (qs != null && !qs.isEmpty()) {
                requestURI += "?" + qs;
            }
            request.setPath(requestURI)
                    .setMethod(exchange.getRequestMethod());
            final HeaderMap inboundRequestHeaders = exchange.getRequestHeaders();
            final HeaderMap outboundRequestHeaders = request.getRequestHeaders();
            copyHeaders(outboundRequestHeaders, inboundRequestHeaders);
            for (Map.Entry<HttpString, ExchangeAttribute> entry : requestHeaders.entrySet()) {
                String headerValue = entry.getValue().readAttribute(exchange);
                if(headerValue == null || headerValue.isEmpty()) {
                    outboundRequestHeaders.remove(entry.getKey());
                } else {
                    outboundRequestHeaders.put(entry.getKey(), headerValue.replace('\n', ' '));
                }
            }
            SocketAddress address = exchange.getConnection().getPeerAddress();
            if (address instanceof InetSocketAddress) {
                outboundRequestHeaders.put(Headers.X_FORWARDED_FOR, ((InetSocketAddress) address).getAddress().getHostAddress());
            } else {
                outboundRequestHeaders.put(Headers.X_FORWARDED_FOR, "localhost");
            }

            SSLSessionInfo sslSessionInfo = exchange.getConnection().getSslSessionInfo();
            if (sslSessionInfo != null) {
                request.putAttachment(ProxiedRequestAttachments.IS_SSL, true);
                X509Certificate[] peerCertificates;
                try {
                    peerCertificates = sslSessionInfo.getPeerCertificateChain();
                    if (peerCertificates.length > 0) {
                        request.putAttachment(ProxiedRequestAttachments.SSL_CERT, Certificates.toPem(peerCertificates[0]));
                    }
                } catch (SSLPeerUnverifiedException e) {
                    //ignore
                } catch (CertificateEncodingException e) {
                    //ignore
                }
                request.putAttachment(ProxiedRequestAttachments.SSL_CYPHER, sslSessionInfo.getCipherSuite());
                request.putAttachment(ProxiedRequestAttachments.SSL_SESSION_ID, sslSessionInfo.getSessionId());
            }


            clientConnection.sendRequest(request, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(ClientExchange result) {

                    result.putAttachment(EXCHANGE, exchange);

                    if (HttpContinue.requiresContinueResponse(exchange)) {
                        result.setContinueHandler(new ContinueNotification() {
                            @Override
                            public void handleContinue(final ClientExchange clientExchange) {
                                HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                                    @Override
                                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                                        //don't care
                                    }

                                    @Override
                                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                                        IoUtils.safeClose(clientConnection);
                                    }
                                });
                            }
                        });
                    }

                    result.setResponseListener(new ResponseCallback(exchange));
                    IoExceptionHandler handler = new IoExceptionHandler(exchange, clientConnection);
                    ChannelListeners.initiateTransfer(Long.MAX_VALUE, exchange.getRequestChannel(), result.getRequestChannel(), ChannelListeners.closingChannelListener(), new HTTPTrailerChannelListener(exchange, result), handler, handler, exchange.getConnection().getBufferPool());
                }

                @Override
                public void failed(IOException e) {
                    exchange.setResponseCode(500);
                    exchange.setPersistent(false);
                    exchange.endExchange();
                }
            });


        }
    }

    private static final class ResponseCallback implements ClientCallback<ClientExchange> {

        private final HttpServerExchange exchange;

        private ResponseCallback(HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientExchange result) {
            HttpServerExchange exchange = result.getAttachment(EXCHANGE);
            final ClientResponse response = result.getResponse();
            final HeaderMap inboundResponseHeaders = response.getResponseHeaders();
            final HeaderMap outboundResponseHeaders = exchange.getResponseHeaders();
            exchange.setResponseCode(response.getResponseCode());
            copyHeaders(outboundResponseHeaders, inboundResponseHeaders);

            if (exchange.isUpgrade()) {
                exchange.upgradeChannel(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                        StreamConnection clientChannel = null;
                        final HttpServerConnection connection = (HttpServerConnection) exchange.getConnection();
                        try {
                            clientChannel = result.getConnection().performUpgrade();
                            connection.resetChannel();

                            StreamConnection streamConnection = connection.getChannel();
                            if (connection.getExtraBytes() != null) {
                                streamConnection.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(streamConnection.getSourceChannel().getConduit(), connection));
                            }
                            ChannelListeners.initiateTransfer(Long.MAX_VALUE, clientChannel.getSourceChannel(), streamConnection.getSinkChannel(), ChannelListeners.closingChannelListener(), ChannelListeners.<StreamSinkChannel>writeShutdownChannelListener(ChannelListeners.<StreamSinkChannel>flushingChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), connection.getBufferPool());
                            ChannelListeners.initiateTransfer(Long.MAX_VALUE, streamConnection.getSourceChannel(), clientChannel.getSinkChannel(), ChannelListeners.closingChannelListener(), ChannelListeners.<StreamSinkChannel>writeShutdownChannelListener(ChannelListeners.<StreamSinkChannel>flushingChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler(), ChannelListeners.closingChannelExceptionHandler(), connection.getBufferPool());
                            nextListener.proceed();
                        } catch (IOException e) {
                            IoUtils.safeClose(connection.getChannel());
                        }
                    }
                });
            }
            IoExceptionHandler handler = new IoExceptionHandler(exchange, result.getConnection());
            ChannelListeners.initiateTransfer(Long.MAX_VALUE, result.getResponseChannel(), exchange.getResponseChannel(), ChannelListeners.closingChannelListener(), new HTTPTrailerChannelListener(result, exchange), handler, handler, exchange.getConnection().getBufferPool());

        }

        @Override
        public void failed(IOException e) {
            exchange.setResponseCode(500);
            exchange.endExchange();
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
            if (trailers != null) {
                target.putAttachment(ChunkedStreamSinkConduit.TRAILERS, trailers);
            }
            try {
                channel.shutdownWrites();
                if (!channel.flush()) {
                    channel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel channel) {
                            channel.suspendWrites();
                            channel.getWriteSetter().set(null);
                        }
                    }, ChannelListeners.closingChannelExceptionHandler()));
                    channel.resumeWrites();
                } else {
                    channel.getWriteSetter().set(null);
                    channel.shutdownWrites();
                }
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(channel);
            }

        }
    }

    private static final class IoExceptionHandler implements ChannelExceptionHandler<Channel> {

        private final HttpServerExchange exchange;
        private final ClientConnection clientConnection;

        private IoExceptionHandler(HttpServerExchange exchange, ClientConnection clientConnection) {
            this.exchange = exchange;
            this.clientConnection = clientConnection;
        }

        @Override
        public void handleException(Channel channel, IOException exception) {
            IoUtils.safeClose(clientConnection);
            if (!exchange.isResponseStarted()) {
                exchange.setResponseCode(500);
            }
            exchange.setPersistent(false);
            exchange.endExchange();
        }
    }
}
