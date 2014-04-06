/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.jsr;

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Session} implementation which makes use of the high-level WebSocket API of undertow under the hood.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UndertowSession implements Session {

    private final String sessionId;
    private final WebSocketChannel webSocketChannel;
    private final FrameHandler frameHandler;
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
    private final Set<Session> openSessions;
    private final String subProtocol;
    private final List<Extension> extensions;
    private volatile int maximumBinaryBufferSize = 0;
    private volatile int maximumTextBufferSize = 0;

    public UndertowSession(WebSocketChannel webSocketChannel, URI requestUri, Map<String, String> pathParameters,
                           Map<String, List<String>> requestParameterMap, EndpointSessionHandler handler, Principal user,
                           InstanceHandle<Endpoint> endpoint, EndpointConfig config, final String queryString,
                           final Encoding encoding, final Set<Session> openSessions, final String subProtocol,
                           final List<Extension> extensions) {
        this.webSocketChannel = webSocketChannel;
        this.queryString = queryString;
        this.encoding = encoding;
        this.openSessions = openSessions;
        container = handler.getContainer();
        this.user = user;
        this.requestUri = requestUri;
        this.requestParameterMap = Collections.unmodifiableMap(requestParameterMap);
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        remote = new WebSocketSessionRemoteEndpoint(webSocketChannel, config, encoding);
        this.endpoint = endpoint;
        webSocketChannel.getCloseSetter().set(new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                close0();
            }
        });
        this.frameHandler = new FrameHandler(this, this.endpoint.getInstance());
        webSocketChannel.getReceiveSetter().set(frameHandler);
        this.sessionId = new SecureRandomSessionIdGenerator().createSessionId();
        this.attrs = Collections.synchronizedMap(new HashMap<String, Object>(config.getUserProperties()));
        this.extensions = extensions;
        this.subProtocol = subProtocol;
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
        close(null);
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        if(closed.compareAndSet(false, true)) {
            try {
                if(closeReason == null) {
                    endpoint.getInstance().onClose(this, new CloseReason(CloseReason.CloseCodes.NO_STATUS_CODE, null));
                } else {
                    endpoint.getInstance().onClose(this, closeReason);
                }
                if(!webSocketChannel.isCloseFrameReceived()) {
                    //if we have already recieved a close frame then the close frame handler
                    //will deal with sending back the reason message
                    if (closeReason == null) {
                        webSocketChannel.sendClose();
                    } else {
                        WebSockets.sendClose(new CloseMessage(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()).toByteBuffer(), webSocketChannel, null);
                    }
                }
            } finally {
                close0();
            }
        }
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
        return new HashSet<Session>(openSessions);
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return extensions;
    }

    void close0() {
        openSessions.remove(this);
        try {
            endpoint.release();
        } finally {
            encoding.close();
        }
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public WebSocketChannel getWebSocketChannel() {
        return webSocketChannel;
    }
}
