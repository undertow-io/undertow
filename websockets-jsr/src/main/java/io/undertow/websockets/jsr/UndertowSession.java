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

import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.impl.WebSocketChannelSession;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Session} implementation which makes use of the high-level WebSocket API of undertow under the hood.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class UndertowSession implements Session {
    // TODO: Think about some more performant datastructure
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());

    private final WebSocketSession session;
    private final WebSocketContainer container;
    private final Principal user;
    private final RemoteEndpoint remote;
    private final Map<String, Object> attrs = new ConcurrentHashMap<String, Object>();
    private final Map<String, List<String>> requestParameterMap;
    private final URI requestUri;
    private final Map<String, String> pathParameters;
    private final Endpoint endpoint;

    public UndertowSession(WebSocketChannelSession session, URI requestUri, Map<String, String> pathParameters, Map<String, List<String>> requestParameterMap, EndpointSessionHandler handler, Principal user, Endpoint endpoint, EndpointConfiguration config) {
        this.session = session;
        container = handler.getContainer();
        this.user = user;
        this.requestUri = requestUri;
        this.requestParameterMap = Collections.unmodifiableMap(requestParameterMap);
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        remote = new WebSocketSessionRemoteEndpoint(session, config);
        session.setFrameHandler(new BasicFrameHandler(this, endpoint));
        this.endpoint = endpoint;
        sessions.add(this);
    }

    @Override
    public WebSocketContainer getContainer() {
        return container;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public synchronized void addMessageHandler(MessageHandler messageHandler) throws IllegalStateException {
        AbstractFrameHandler handler = (AbstractFrameHandler<?> ) session.getFrameHandler();
        if (messageHandler instanceof MessageHandler.Basic) {
            if (handler instanceof BasicFrameHandler) {
                handler.addHandler(messageHandler);
            } else {
                if (handler.getHandlers().isEmpty()) {
                    handler = new BasicFrameHandler(this, endpoint);
                    handler.addHandler(messageHandler);
                    session.setFrameHandler(handler);
                } else {
                    // Mixed Async and Basic handlers need to switch to support both
                    switchToMixed(handler, messageHandler);
                }
            }
        } else  if (messageHandler instanceof MessageHandler.Async) {
            if (handler instanceof AsyncFrameHandler) {
                handler.addHandler(messageHandler);
            } else {
                if (handler.getHandlers().isEmpty()) {
                    handler = new AsyncFrameHandler(this, endpoint);
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
        handler = new MixedFrameHandler(this, endpoint);
        for (MessageHandler h: handlers) {
            handler.addHandler(h);
        }
        handler.addHandler(messageHandler);
        session.setFrameHandler(handler);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public synchronized Set<MessageHandler> getMessageHandlers() {
        return ((AbstractFrameHandler)session.getFrameHandler()).getHandlers();
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
            for (MessageHandler h: handlers) {
                if (h instanceof MessageHandler.Async) {
                    async = true;
                } else if (h instanceof MessageHandler.Basic) {
                    basic = true;
                }
                if (basic && async) {
                    return;
                }
            }
            // This means we not have the case of mixed Async and Basic handlers so we can switch back to the
            // most optimized implementation
            if (basic) {
                handler = new BasicFrameHandler(this, endpoint);
            } else if (async) {
                handler = new AsyncFrameHandler(this, endpoint);
            }
            for (MessageHandler h: handlers) {
                handler.addHandler(h);
            }
            session.setFrameHandler(handler);
        }
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
    public long getTimeout() {
        return session.getIdleTimeout();
    }

    @Override
    public void setTimeout(long timeout) {
        session.setIdleTimeout((int) timeout);
    }

    @Override
    public RemoteEndpoint getRemote() {
        return remote;
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public void close() throws IOException {
        session.sendClose(null);
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        if (closeReason == null) {
            session.sendClose(null);
        } else {
            session.sendClose(new io.undertow.websockets.api.CloseReason(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()));
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
        return requestUri.getQuery();
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
    public Set<Session> getOpenSessions() {
        return new HashSet<Session>(sessions);
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return Collections.emptyList();
    }

    void close0() {
        sessions.remove(this);
    }
}
