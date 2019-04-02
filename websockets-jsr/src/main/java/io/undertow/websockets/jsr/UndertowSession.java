/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.websockets.jsr;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.servlet.api.InstanceHandle;

/**
 * {@link Session} implementation which makes use of the high-level WebSocket API of undertow under the hood.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UndertowSession implements Session {

    private final String sessionId;
    private Channel channel;
    private FrameHandler frameHandler;
    private final ServerWebSocketContainer container;
    private final Principal user;
    private final WebSocketSessionRemoteEndpoint remote;
    private final Map<String, Object> attrs;
    private final Map<String, List<String>> requestParameterMap;
    private final URI requestUri;
    private final String queryString;
    private final Map<String, String> pathParameters;
    private final InstanceHandle<Endpoint> endpoint;
    private final Encoding encoding;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final SessionContainer openSessions;
    private final String subProtocol;
    private final List<Extension> extensions;
    private final EndpointConfig config;
    private final Executor executor;
    private volatile int maximumBinaryBufferSize = 0;
    private volatile int maximumTextBufferSize = 0;
    private volatile boolean localClose;
    private int disconnectCount = 0;
    private int failedCount = 0;
    private ConfiguredServerEndpoint configuredServerEndpoint;
    private final WebsocketConnectionBuilder clientConnectionBuilder;

    public UndertowSession(Channel channel, URI requestUri, Map<String, String> pathParameters,
                           Map<String, List<String>> requestParameterMap, EndpointSessionHandler handler, Principal user,
                           InstanceHandle<Endpoint> endpoint, EndpointConfig config, final String queryString,
                           final Encoding encoding, final SessionContainer openSessions, final String subProtocol,
                           final List<Extension> extensions, WebsocketConnectionBuilder clientConnectionBuilder,
                           Executor executor) {
        this.clientConnectionBuilder = clientConnectionBuilder;
        assert openSessions != null;
        this.channel = channel;
        this.queryString = queryString;
        this.encoding = encoding;
        this.openSessions = openSessions;
        container = handler.getContainer();
        this.user = user;
        this.requestUri = requestUri;
        this.requestParameterMap = Collections.unmodifiableMap(requestParameterMap);
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        this.config = config;
        remote = new WebSocketSessionRemoteEndpoint(this, encoding);
        this.endpoint = endpoint;
        this.sessionId = new SecureRandomSessionIdGenerator().createSessionId();
        this.attrs = Collections.synchronizedMap(new HashMap<>(config.getUserProperties()));
        this.extensions = extensions;
        this.subProtocol = subProtocol;
        this.executor = executor;
        setupWebSocketChannel(channel);
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public ServerWebSocketContainer getContainer() {
        return container;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public synchronized void addMessageHandler(MessageHandler messageHandler) throws IllegalStateException {
        frameHandler.addHandler(messageHandler);
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        frameHandler.addHandler(clazz, handler);
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        frameHandler.addHandler(clazz, handler);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public synchronized Set<MessageHandler> getMessageHandlers() {
        return frameHandler.getHandlers();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public synchronized void removeMessageHandler(MessageHandler messageHandler) {
        frameHandler.removeHandler(messageHandler);
    }

    @Override
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return subProtocol == null ? "" : subProtocol;
    }

    @Override
    public boolean isSecure() {
        return channel.pipeline().get(SslHandler.class) != null;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public long getMaxIdleTimeout() {
        //return webSocketChannel.getIdleTimeout();
        return -1;
    }

    @Override
    public void setMaxIdleTimeout(final long milliseconds) {
        //webSocketChannel.setIdleTimeout(milliseconds);
    }

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public void close() throws IOException {
        close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, null));
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        localClose = true;
        closeInternal(closeReason);
    }

    public void closeInternal() throws IOException {
        closeInternal(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, null));
    }

    public void closeInternal(CloseReason closeReason) throws IOException {
        if (closed.compareAndSet(false, true)) {
            channel.writeAndFlush(new CloseWebSocketFrame(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()))
                    .addListener(new GenericFutureListener<Future<? super Void>>() {
                        @Override
                        public void operationComplete(Future<? super Void> future) throws Exception {
                            channel.close();
                        }
                    });
            getContainer().invokeEndpointMethod(getExecutor(), new Runnable() {
                @Override
                public void run() {
                    endpoint.getInstance().onClose(UndertowSession.this, closeReason);
                }
            });
            //TODO: there is a lot of spec required behaviour here
        }
    }

    private void handleReconnect(final long reconnect) {
        JsrWebSocketLogger.REQUEST_LOGGER.debugf("Attempting reconnect in %s ms for session %s", reconnect, this);
        channel.eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                ChannelFuture channelFuture = clientConnectionBuilder.connect();
                channelFuture
                        .addListener(new GenericFutureListener<Future<? super Void>>() {
                            @Override
                            public void operationComplete(Future<? super Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    long timeout = container.getWebSocketReconnectHandler().reconnectFailed(new IOException(future.cause()), getRequestURI(), UndertowSession.this, ++failedCount);
                                    if (timeout >= 0) {
                                        handleReconnect(timeout);
                                    }
                                } else {
                                    closed.set(false);
                                    channel = channelFuture.channel();
                                    UndertowSession.this.setupWebSocketChannel(channel);
                                    localClose = false;
                                    endpoint.getInstance().onOpen(UndertowSession.this, config);

                                }
                            }
                        });
            }
        }, reconnect, TimeUnit.MILLISECONDS);
    }

    public void forceClose() {
        try {
            channel.close().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URI getRequestURI() {
        return requestUri;
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return requestParameterMap;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return attrs;
    }

    @Override
    public Principal getUserPrincipal() {
        return user;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int i) {
        maximumBinaryBufferSize = i;
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return maximumBinaryBufferSize;
        //return (int) webSocketChannel.getMaximumBinaryFrameSize();
    }

    @Override
    public void setMaxTextMessageBufferSize(int i) {
        maximumTextBufferSize = i;
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return maximumTextBufferSize;
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        return remote.getAsync();
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        return remote.getBasic();
    }

    @Override
    public Set<Session> getOpenSessions() {
        return new HashSet<>(openSessions.getOpenSessions());
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return extensions;
    }

    public ConfiguredServerEndpoint getConfiguredServerEndpoint() {
        return configuredServerEndpoint;
    }

    public UndertowSession setConfiguredServerEndpoint(ConfiguredServerEndpoint configuredServerEndpoint) {
        this.configuredServerEndpoint = configuredServerEndpoint;
        return this;
    }

    void close0() {
        //we use the executor to preserve ordering
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    endpoint.release();
                } finally {
                    try {
                        encoding.close();
                    } finally {
                        openSessions.removeOpenSession(UndertowSession.this);
                    }
                }
            }
        });
    }

    public Encoding getEncoding() {
        return encoding;
    }

    private void setupWebSocketChannel(Channel webSocketChannel) {
        this.frameHandler = new FrameHandler(this, this.endpoint.getInstance());
        webSocketChannel.pipeline().addLast(frameHandler);

    }

    public Executor getExecutor() {
        return executor;
    }

    boolean isSessionClosed() {
        return closed.get();
    }
}
