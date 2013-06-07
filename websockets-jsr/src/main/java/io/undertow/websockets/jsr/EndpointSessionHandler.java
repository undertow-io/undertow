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

import java.net.URI;
import java.util.Collections;
import java.util.List;

import javax.websocket.Endpoint;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.impl.WebSocketChannelSession;
import io.undertow.websockets.jsr.handshake.HandshakeUtil;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.IoUtils;

/**
 * {@link WebSocketSessionHandler} implementation which will setuo the {@link UndertowSession} and notify
 * the {@link Endpoint} about the new session.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class EndpointSessionHandler implements WebSocketSessionHandler {
    private final ServerWebSocketContainer container;

    public EndpointSessionHandler(ServerWebSocketContainer container) {
        this.container = container;
    }

    /**
     * Returns the {@link ServerWebSocketContainer} which was used for this {@link WebSocketSessionHandler}.
     */
    ServerWebSocketContainer getContainer() {
        return container;
    }

    @Override
    public void onSession(WebSocketSession s, WebSocketHttpExchange exchange) {
        WebSocketChannelSession channelSession = (WebSocketChannelSession) s;
        ConfiguredServerEndpoint config = HandshakeUtil.getConfig(channelSession.getChannel());


        try {
            InstanceFactory<Endpoint> endpointFactory = config.getEndpointFactory();
            final InstanceHandle<Endpoint> instance;
            if(endpointFactory != null) {
                instance = endpointFactory.createInstance();
            } else {
                instance = new ImmediateInstanceHandle<Endpoint>((Endpoint) config.getEndpointConfiguration().getConfigurator().getEndpointInstance(config.getEndpointConfiguration().getEndpointClass()));
            }

            UndertowSession session = new UndertowSession(channelSession, URI.create(exchange.getRequestURI()), exchange.getAttachment(HandshakeUtil.PATH_PARAMS), Collections.<String, List<String>>emptyMap(), this, null, instance, config.getEndpointConfiguration(), exchange.getQueryString(), config.getEncodingFactory().createEncoding(config.getEndpointConfiguration()), config.getOpenSessions());
            config.getOpenSessions().add(session);
            session.setMaxBinaryMessageBufferSize(getContainer().getDefaultMaxBinaryMessageBufferSize());
            session.setMaxTextMessageBufferSize(getContainer().getDefaultMaxTextMessageBufferSize());
            //session.setTimeout(getContainer().getMaxSessionIdleTimeout());
            session.getAsyncRemote().setSendTimeout(getContainer().getDefaultAsyncSendTimeout());
            instance.getInstance().onOpen(session, config.getEndpointConfiguration());
        } catch (InstantiationException e) {
            JsrWebSocketLogger.REQUEST_LOGGER.endpointCreationFailed(e);
            IoUtils.safeClose(channelSession.getChannel());
        }
    }
}
