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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpoint;
import io.undertow.websockets.jsr.handshake.HandshakeUtil;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.IoUtils;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;

/**
 * {@link WebSocketConnectionCallback} implementation which will setuo the {@link UndertowSession} and notify
 * the {@link Endpoint} about the new session.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class EndpointSessionHandler implements WebSocketConnectionCallback {
    private final ServerWebSocketContainer container;

    public EndpointSessionHandler(ServerWebSocketContainer container) {
        this.container = container;
    }

    /**
     * Returns the {@link ServerWebSocketContainer} which was used for this {@link WebSocketConnectionCallback}.
     */
    ServerWebSocketContainer getContainer() {
        return container;
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        ConfiguredServerEndpoint config = HandshakeUtil.getConfig(channel);
        try {
            if(container.isClosed()) {
                //if the underlying container is closed we just reject
                channel.sendClose();
                channel.resumeReceives();
                return;
            }
            InstanceFactory<?> endpointFactory = config.getEndpointFactory();
            ServerEndpointConfig.Configurator configurator = config.getEndpointConfiguration().getConfigurator();
            final InstanceHandle<?> instance;
            DefaultContainerConfigurator.setCurrentInstanceFactory(endpointFactory);
            final Object instanceFromConfigurator = configurator.getEndpointInstance(config.getEndpointConfiguration().getEndpointClass());
            final InstanceHandle<?> factoryInstance = DefaultContainerConfigurator.clearCurrentInstanceFactory();
            if (factoryInstance == null) {
                instance = new ImmediateInstanceHandle<>(instanceFromConfigurator);
            } else if (factoryInstance.getInstance() == instanceFromConfigurator) {
                instance = factoryInstance;
            } else {
                //the default instance has been wrapped
                instance = new InstanceHandle<Object>() {
                    @Override
                    public Object getInstance() {
                        return instanceFromConfigurator;
                    }

                    @Override
                    public void release() {
                        factoryInstance.release();
                    }
                };
            }

            ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            Principal principal = exchange.getAttachment(HandshakeUtil.PRINCIPAL);
            if(principal == null) {
                if(src.getServletRequest() instanceof HttpServletRequest) {
                    principal = ((HttpServletRequest)src.getServletRequest()).getUserPrincipal();
                } else {
                    principal = src.getOriginalRequest().getUserPrincipal();
                }
            }
            final InstanceHandle<Endpoint> endpointInstance;
            if(config.getAnnotatedEndpointFactory() != null) {
                final AnnotatedEndpoint annotated = config.getAnnotatedEndpointFactory().createInstance(instance);
                endpointInstance = new InstanceHandle<Endpoint>() {
                    @Override
                    public Endpoint getInstance() {
                        return annotated;
                    }

                    @Override
                    public void release() {
                        instance.release();
                    }
                };
            } else {
                endpointInstance = (InstanceHandle<Endpoint>) instance;
            }

            UndertowSession session = new UndertowSession(channel, URI.create(exchange.getRequestURI()), exchange.getAttachment(HandshakeUtil.PATH_PARAMS), exchange.getRequestParameters(), this, principal, endpointInstance, config.getEndpointConfiguration(), exchange.getQueryString(), config.getEncodingFactory().createEncoding(config.getEndpointConfiguration()), config, channel.getSubProtocol(), Collections.<Extension>emptyList(), null);
            config.addOpenSession(session);

            session.setMaxBinaryMessageBufferSize(getContainer().getDefaultMaxBinaryMessageBufferSize());
            session.setMaxTextMessageBufferSize(getContainer().getDefaultMaxTextMessageBufferSize());
            session.setMaxIdleTimeout(getContainer().getDefaultMaxSessionIdleTimeout());
            session.getAsyncRemote().setSendTimeout(getContainer().getDefaultAsyncSendTimeout());
            try {
                endpointInstance.getInstance().onOpen(session, config.getEndpointConfiguration());
            } catch (Exception e) {
                endpointInstance.getInstance().onError(session, e);
                IoUtils.safeClose(session);
            }
            channel.resumeReceives();
        } catch (Exception e) {
            JsrWebSocketLogger.REQUEST_LOGGER.endpointCreationFailed(e);
            IoUtils.safeClose(channel);
        }
    }
}
