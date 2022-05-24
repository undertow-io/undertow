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

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.util.WorkerUtils;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.IoUtils;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Session} implementation which makes use of the high-level WebSocket API of undertow under the hood.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UndertowSession implements Session {

    private final String sessionId;
    private WebSocketChannel webSocketChannel;
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
    private final WebSocketClient.ConnectionBuilder clientConnectionBuilder;
    private final EndpointConfig config;
    private volatile int maximumBinaryBufferSize = 0;
    private volatile int maximumTextBufferSize = 0;
    private volatile boolean localClose;
    private int disconnectCount = 0;
    private int failedCount = 0;

    UndertowSession(WebSocketChannel webSocketChannel, URI requestUri, Map<String, String> pathParameters,
                    Map<String, List<String>> requestParameterMap, EndpointSessionHandler handler, Principal user,
                    InstanceHandle<Endpoint> endpoint, EndpointConfig config, final String queryString,
                    final Encoding encoding, final SessionContainer openSessions, final String subProtocol,
                    final List<Extension> extensions, WebSocketClient.ConnectionBuilder clientConnectionBuilder) {
        assert openSessions != null;
        this.webSocketChannel = webSocketChannel;
        this.queryString = queryString;
        this.encoding = encoding;
        this.openSessions = openSessions;
        this.clientConnectionBuilder = clientConnectionBuilder;
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
        setupWebSocketChannel(webSocketChannel);
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

    /**
     * sets the recieve listener This should only be used for annotated endpoints.
     *
     * @param handler The handler
     */
    public void setReceiveListener(final ChannelListener<WebSocketChannel> handler) {
        webSocketChannel.getReceiveSetter().set(handler);
    }

    @Override
    public String getProtocolVersion() {
        return webSocketChannel.getVersion().toHttpHeaderValue();
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return subProtocol == null ? "" : subProtocol;
    }

    @Override
    public boolean isSecure() {
        return webSocketChannel.isSecure();
    }

    @Override
    public boolean isOpen() {
        return webSocketChannel.isOpen();
    }

    @Override
    public long getMaxIdleTimeout() {
        return webSocketChannel.getIdleTimeout();
    }

    @Override
    public void setMaxIdleTimeout(final long milliseconds) {
        webSocketChannel.setIdleTimeout(milliseconds);
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
        if(closed.compareAndSet(false, true)) {
            try {
                try {
                    if (!webSocketChannel.isCloseFrameReceived() && !webSocketChannel.isCloseFrameSent()) {
                        //if we have already recieved a close frame then the close frame handler
                        //will deal with sending back the reason message
                        if (closeReason == null || closeReason.getCloseCode().getCode() == CloseReason.CloseCodes.NO_STATUS_CODE.getCode()) {
                            webSocketChannel.sendClose();
                        } else {
                            WebSockets.sendClose(new CloseMessage(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()).toByteBuffer(), webSocketChannel, null);
                        }
                    }
                } finally {
                    try {
                        String reason = null;
                        CloseReason.CloseCode code = CloseReason.CloseCodes.NO_STATUS_CODE;
                        if(webSocketChannel.getCloseCode() != -1) {
                            reason = webSocketChannel.getCloseReason();
                            code = CloseReason.CloseCodes.getCloseCode(webSocketChannel.getCloseCode());
                        } else if(closeReason != null) {
                            reason = closeReason.getReasonPhrase();
                            code = closeReason.getCloseCode();
                        }
                        //horrible hack
                        //the spec says that if we (the local container) close locally then we need to use 1006
                        //although the TCK does not expect this behaviour for TOO_BIG and VIOLATED_POLICY
                        //we need to really clean up the close behaviour in the next spec
                        if(!webSocketChannel.isCloseInitiatedByRemotePeer() && !localClose && code.getCode() != CloseReason.CloseCodes.TOO_BIG.getCode() && code.getCode() != CloseReason.CloseCodes.VIOLATED_POLICY.getCode()) {
                            //2.1.5: we must use 1006 if the close was initiated locally
                            //however we only do this for normal closure
                            //if the close was due to another reason such as a message being too long we need to report the real reason
                            code = CloseReason.CloseCodes.CLOSED_ABNORMALLY;
                        }
                        endpoint.getInstance().onClose(this, new CloseReason(code, reason));
                    } catch (Exception e) {
                        endpoint.getInstance().onError(this, e);
                    }
                }
            } finally {
                close0();
                if(clientConnectionBuilder != null && !localClose) {
                    WebSocketReconnectHandler webSocketReconnectHandler = container.getWebSocketReconnectHandler();
                    if (webSocketReconnectHandler != null) {
                        JsrWebSocketLogger.REQUEST_LOGGER.debugf("Calling reconnect handler for %s", this);
                        long reconnect = webSocketReconnectHandler.disconnected(closeReason, requestUri, this, ++disconnectCount);
                        if (reconnect >= 0) {
                            handleReconnect(reconnect);
                        }
                    }
                }
            }
        }
    }

    private void handleReconnect(final long reconnect) {
        JsrWebSocketLogger.REQUEST_LOGGER.debugf("Attempting reconnect in %s ms for session %s", reconnect, this);
        WorkerUtils.executeAfter(webSocketChannel.getIoThread(), new Runnable() {
            @Override
            public void run() {
                clientConnectionBuilder.connect().addNotifier(new IoFuture.HandlingNotifier<WebSocketChannel, Object>() {
                    @Override
                    public void handleDone(WebSocketChannel data, Object attachment) {
                        closed.set(false);
                        UndertowSession.this.webSocketChannel = data;
                        UndertowSession.this.setupWebSocketChannel(data);
                        localClose = false;
                        endpoint.getInstance().onOpen(UndertowSession.this, config);
                        webSocketChannel.resumeReceives();
                    }

                    @Override
                    public void handleFailed(IOException exception, Object attachment) {
                        long timeout = container.getWebSocketReconnectHandler().reconnectFailed(exception, getRequestURI(), UndertowSession.this, ++failedCount);
                        if(timeout >= 0) {
                            handleReconnect(timeout);
                        }
                    }
                }, null);
            }
        }, reconnect, TimeUnit.MILLISECONDS);
    }

    public void forceClose() {
        IoUtils.safeClose(webSocketChannel);
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

    public WebSocketChannel getWebSocketChannel() {
        return webSocketChannel;
    }

    private void setupWebSocketChannel(WebSocketChannel webSocketChannel) {
        this.frameHandler = new FrameHandler(this, this.endpoint.getInstance());
        webSocketChannel.getReceiveSetter().set(frameHandler);
        webSocketChannel.addCloseTask(new ChannelListener<WebSocketChannel>() {
            @Override
            public void handleEvent(WebSocketChannel channel) {
                //so this puts us in an interesting position. We know the underlying
                //TCP connection has been torn down, however this may have involved reading
                //a close frame, which will be delivered shortly
                //to get around this we schedule the code in the IO thread, so if there is a close
                //frame awaiting delivery it will be delivered before the close
                final Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        //we delegate this execution to the IO thread
                        try {
                            closeInternal(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, null));
                        } catch (IOException e) {
                            //ignore
                        }
                    }
                };
                try {
                    channel.getIoThread().execute(task);
                } catch (RejectedExecutionException e) {
                    task.run(); //thread is shutting down
                }
            }
        });
    }

    public Executor getExecutor() {
        return frameHandler.getExecutor();
    }

    boolean isSessionClosed() {
        return closed.get();
    }
}
