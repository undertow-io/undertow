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

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.impl.WebSocketChannelSession;
import org.xnio.ChannelListener;

/**
 * {@link Session} implementation which makes use of the high-level WebSocket API of undertow under the hood.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UndertowSession implements Session {

    private final WebSocketSession session;
    private final WebSocketContainer container;
    private final Principal user;
    private final WebSocketSessionRemoteEndpoint remote;
    private final Map<String, Object> attrs = new ConcurrentHashMap<String, Object>();
    private final Map<String, List<String>> requestParameterMap;
    private final URI requestUri;
    private final String queryString;
    private final Map<String, String> pathParameters;
    private final InstanceHandle<Endpoint> endpoint;
    private final Encoding encoding;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Session> openSessions;

    public UndertowSession(WebSocketChannelSession session, URI requestUri, Map<String, String> pathParameters, Map<String, List<String>> requestParameterMap, EndpointSessionHandler handler, Principal user, InstanceHandle<Endpoint> endpoint, EndpointConfig config, final String queryString, final Encoding encoding, final Set<Session> openSessions) {
        this.session = session;
        this.queryString = queryString;
        this.encoding = encoding;
        this.openSessions = openSessions;
        container = handler.getContainer();
        this.user = user;
        this.requestUri = requestUri;
        this.requestParameterMap = Collections.unmodifiableMap(requestParameterMap);
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        remote = new WebSocketSessionRemoteEndpoint(session, config, encoding);
        session.setFrameHandler(new WholeFrameHandler(this, endpoint.getInstance()));
        this.endpoint = endpoint;
        session.getChannel().getCloseSetter().set(new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                close0();
            }
        });
    }

    @Override
    public WebSocketContainer getContainer() {
        return container;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public synchronized void addMessageHandler(MessageHandler messageHandler) throws IllegalStateException {
        AbstractFrameHandler handler = (AbstractFrameHandler<?>) session.getFrameHandler();
        if (messageHandler instanceof MessageHandler.Whole) {
            if (handler instanceof WholeFrameHandler) {
                handler.addHandler(messageHandler);
            } else {
                if (handler.getHandlers().isEmpty()) {
                    handler = new WholeFrameHandler(this, endpoint.getInstance());
                    handler.addHandler(messageHandler);
                    session.setFrameHandler(handler);
                } else {
                    // Mixed Async and Basic handlers need to switch to support both
                    switchToMixed(handler, messageHandler);
                }
            }
        } else if (messageHandler instanceof MessageHandler.Partial) {
            if (handler instanceof PartialFrameHandler) {
                handler.addHandler(messageHandler);
            } else {
                if (handler.getHandlers().isEmpty()) {
                    handler = new PartialFrameHandler(this, endpoint.getInstance());
                    handler.addHandler(messageHandler);
                    session.setFrameHandler(handler);
                } else {
                    // Mixed Async and Basic handlers need to switch to support both
                    switchToMixed(handler, messageHandler);
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void switchToMixed(AbstractFrameHandler handler, MessageHandler messageHandler) {
        Set<MessageHandler> handlers = handler.getHandlers();
        handler = new MixedFrameHandler(this, endpoint.getInstance());
        for (MessageHandler h : handlers) {
            handler.addHandler(h);
        }
        handler.addHandler(messageHandler);
        session.setFrameHandler(handler);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public synchronized Set<MessageHandler> getMessageHandlers() {
        return ((AbstractFrameHandler) session.getFrameHandler()).getHandlers();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public synchronized void removeMessageHandler(MessageHandler messageHandler) {
        AbstractFrameHandler handler = (AbstractFrameHandler) session.getFrameHandler();
        handler.removeHandler(messageHandler);
        if (handler instanceof MixedFrameHandler) {
            Set<MessageHandler> handlers = handler.getHandlers();
            boolean basic = false;
            boolean async = false;
            for (MessageHandler h : handlers) {
                if (h instanceof MessageHandler.Partial) {
                    async = true;
                } else if (h instanceof MessageHandler.Whole) {
                    basic = true;
                }
                if (basic && async) {
                    return;
                }
            }
            // This means we not have the case of mixed Async and Basic handlers so we can switch back to the
            // most optimized implementation
            if (basic) {
                handler = new WholeFrameHandler(this, endpoint.getInstance());
            } else if (async) {
                handler = new PartialFrameHandler(this, endpoint.getInstance());
            }
            for (MessageHandler h : handlers) {
                handler.addHandler(h);
            }
            session.setFrameHandler(handler);
        }
    }

    /**
     * sets the frame handler. This should only be used for annotated endpoints.
     *
     * @param handler The handler
     */
    public void setFrameHandler(final FrameHandler handler) {
        session.setFrameHandler(handler);
    }

    @Override
    public String getProtocolVersion() {
        return session.getProtocolVersion();
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return session.isSecure();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public long getMaxIdleTimeout() {
        return 0;
    }

    @Override
    public void setMaxIdleTimeout(final long milliseconds) {

    }

    @Override
    public String getId() {
        return session.getId();
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
                if(!session.isCloseFrameReceived()) {
                    //if we have already recieved a close frame then the close frame handler
                    //will deal with sending back the reason message
                    if (closeReason == null) {
                        session.sendClose(null);
                    } else {
                        session.sendClose(new io.undertow.websockets.api.CloseReason(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()));
                    }
                }
            } finally {
                close0();
            }
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
        session.setMaximumBinaryFrameSize(i);
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return (int) session.getMaximumBinaryFrameSize();
    }

    @Override
    public void setMaxTextMessageBufferSize(int i) {
        session.setMaximumTextFrameSize(i);
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return (int) session.getMaximumTextFrameSize();
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
        return new HashSet<>(openSessions);
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return Collections.emptyList();
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
}
