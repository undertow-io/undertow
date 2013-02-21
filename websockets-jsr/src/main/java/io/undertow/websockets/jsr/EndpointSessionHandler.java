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

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.impl.WebSocketChannelSession;
import org.xnio.IoUtils;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfiguration;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * {@link WebSocketSessionHandler} implementation which will setuo the {@link UndertowSession} and notify
 * the {@link Endpoint} about the new session.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class EndpointSessionHandler implements WebSocketSessionHandler {
    private final ServerWebSocketContainer container;

    EndpointSessionHandler(ServerWebSocketContainer container) {
        this.container = container;
    }

    /**
     * Returns the {@link ServerWebSocketContainer} which was used for this {@link WebSocketSessionHandler}.
     */
    ServerWebSocketContainer getContainer() {
        return container;
    }

    @Override
    public void onSession(WebSocketSession s, HttpServerExchange exchange) {
        WebSocketChannelSession channelSession = (WebSocketChannelSession) s;
        ServerEndpointConfiguration config = HandshakeUtil.getConfig(channelSession.getChannel());

        try {
            Endpoint endpoint = container.getEndpointFactory().createEndpoint(config.getEndpointClass());

            UndertowSession session = new UndertowSession(channelSession, URI.create(exchange.getRequestURI()), Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), this, null, endpoint, config);
            session.setMaxBinaryMessageBufferSize(getContainer().getDefaultMaxBinaryMessageBufferSize());
            session.setMaxTextMessageBufferSize(getContainer().getDefaultMaxTextMessageBufferSize());
            session.setTimeout(getContainer().getMaxSessionIdleTimeout());
            session.getRemote().setAsyncSendTimeout(getContainer().getDefaultAsyncSendTimeout());
            endpoint.onOpen(session, config);
        } catch (InstantiationException e) {
            JsrWebSocketLogger.REQUEST_LOGGER.endpointCreationFailed(e);
            IoUtils.safeClose(channelSession.getChannel());
        }
    }
}
