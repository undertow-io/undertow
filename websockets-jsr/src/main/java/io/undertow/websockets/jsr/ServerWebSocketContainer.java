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
package io.undertow.websockets.jsr;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfiguration;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * {@link WebSocketContainer} implementation which allows to deploy endpoints for a server. This instance
 * should be added as {@link HttpHandler}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ServerWebSocketContainer implements WebSocketContainer, HttpHandler {
    private volatile long defaultAsyncSendTimeout;
    private volatile long maxSessionIdleTimeout;
    private volatile int defaultMaxBinaryMessageBufferSize;
    private volatile int defaultMaxTextMessageBufferSize;
    private final HttpHandler handler;
    private final EndpointFactory factory;

    public ServerWebSocketContainer(EndpointFactory factory, ServerEndpointConfiguration... configs) {
        this.factory = factory;
        handler = new JsrWebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(new UuidWebSocketSessionIdGenerator(),
                new EndpointSessionHandler(this), false), configs);
    }

    EndpointFactory getEndpointFactory () {
        return factory;
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncSendTimeout;
    }

    @Override
    public void setAsyncSendTimeout(long defaultAsyncSendTimeout) {
        this.defaultAsyncSendTimeout = defaultAsyncSendTimeout;
    }

    @Override
    public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException {
        throw JsrWebSocketMessages.MESSAGES.clientNotSupported();
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> aClass, ClientEndpointConfiguration clientEndpointConfiguration, URI uri) throws DeploymentException {
        throw JsrWebSocketMessages.MESSAGES.clientNotSupported();
    }

    @Override
    public long getMaxSessionIdleTimeout() {
        return maxSessionIdleTimeout;
    }

    @Override
    public void setMaxSessionIdleTimeout(long maxSessionIdleTimeout) {
        this.maxSessionIdleTimeout = maxSessionIdleTimeout;
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return defaultMaxBinaryMessageBufferSize;
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int defaultMaxBinaryMessageBufferSize) {
        this.defaultMaxBinaryMessageBufferSize = defaultMaxBinaryMessageBufferSize;
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return defaultMaxTextMessageBufferSize;
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int defaultMaxTextMessageBufferSize) {
        this.defaultMaxTextMessageBufferSize = defaultMaxTextMessageBufferSize;
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        handler.handleRequest(exchange);
    }
}
