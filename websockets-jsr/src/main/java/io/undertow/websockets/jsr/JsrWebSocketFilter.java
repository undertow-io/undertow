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

import static io.undertow.websockets.jsr.ServerWebSocketContainer.WebSocketHandshakeHolder;

import java.io.IOException;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.websocket.CloseReason;
import jakarta.websocket.server.ServerContainer;

import org.xnio.ChannelListener;
import org.xnio.StreamConnection;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatcher;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.jsr.handshake.HandshakeUtil;

/**
 * Filter that provides HTTP upgrade functionality. This should be run after all user filters, but before any servlets.
 * <p>
 * The use of a filter rather than a servlet allows for normal HTTP requests to be served from the same location
 * as a web socket endpoint if no upgrade header is found.
 * <p>
 *
 * @author Stuart Douglas
 */
public class JsrWebSocketFilter implements Filter {

    private WebSocketConnectionCallback callback;
    private PathTemplateMatcher<WebSocketHandshakeHolder> pathTemplateMatcher;
    private Set<WebSocketChannel> peerConnections;
    private ServerWebSocketContainer container;

    private static final String SESSION_ATTRIBUTE = "io.undertow.websocket.current-connections";


    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        peerConnections = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketChannel, Boolean>());
        container = (ServerWebSocketContainer) filterConfig.getServletContext().getAttribute(ServerContainer.class.getName());
        container.deploymentComplete();
        pathTemplateMatcher = new PathTemplateMatcher<>();
        WebSocketDeploymentInfo info = (WebSocketDeploymentInfo)filterConfig.getServletContext().getAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME);
        for (ConfiguredServerEndpoint endpoint : container.getConfiguredServerEndpoints()) {
            if (info == null || info.getExtensions().isEmpty()) {
                pathTemplateMatcher.add(endpoint.getPathTemplate(), ServerWebSocketContainer.handshakes(endpoint));
            } else {
                pathTemplateMatcher.add(endpoint.getPathTemplate(), ServerWebSocketContainer.handshakes(endpoint, info.getExtensions()));
            }
        }
        this.callback = new EndpointSessionHandler(container);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (req.getHeader(Headers.UPGRADE_STRING) != null) {
            final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp, peerConnections);
            String path;
            if (req.getPathInfo() == null) {
                path = req.getServletPath();
            } else {
                path = req.getServletPath() + req.getPathInfo();
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            PathTemplateMatcher.PathMatchResult<WebSocketHandshakeHolder> matchResult = pathTemplateMatcher.match(path);
            if (matchResult != null) {
                Handshake handshaker = null;
                for (Handshake method : matchResult.getValue().handshakes) {
                    if (method.matches(facade)) {
                        handshaker = method;
                        break;
                    }
                }

                if (handshaker != null) {
                    if(container.isClosed()) {
                        resp.sendError(StatusCodes.SERVICE_UNAVAILABLE);
                        return;
                    }
                    facade.putAttachment(HandshakeUtil.PATH_PARAMS, matchResult.getParameters());
                    facade.putAttachment(HandshakeUtil.PRINCIPAL, req.getUserPrincipal());
                    final Handshake selected = handshaker;
                    ServletRequestContext src = ServletRequestContext.requireCurrent();
                    final HttpSessionImpl session = src.getCurrentServletContext().getSession(src.getExchange(), false);
                    facade.upgradeChannel(new HttpUpgradeListener() {
                        @Override
                        public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                            HandshakeUtil.propagate(exchange, facade);
                            WebSocketChannel channel = selected.createChannel(facade, streamConnection, facade.getBufferPool());
                            peerConnections.add(channel);
                            if(session != null) {
                                final Session underlying;
                                if (System.getSecurityManager() == null) {
                                    underlying = session.getSession();
                                } else {
                                    underlying = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
                                }
                                List<WebSocketChannel> connections;
                                synchronized (underlying) {
                                    connections = (List<WebSocketChannel>) underlying.getAttribute(SESSION_ATTRIBUTE);
                                    if(connections == null) {
                                        underlying.setAttribute(SESSION_ATTRIBUTE, connections = new ArrayList<>());
                                    }
                                    connections.add(channel);
                                }
                                final List<WebSocketChannel> finalConnections = connections;
                                channel.addCloseTask(new ChannelListener<WebSocketChannel>() {
                                    @Override
                                    public void handleEvent(WebSocketChannel channel) {
                                        synchronized (underlying) {
                                            finalConnections.remove(channel);
                                        }
                                    }
                                });
                            }
                            callback.onConnect(facade, channel);
                        }
                    });
                    handshaker.handshake(facade);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }


    public static class LogoutListener implements HttpSessionListener {

        @Override
        public void sessionCreated(HttpSessionEvent se) {

        }

        @Override
        public void sessionDestroyed(HttpSessionEvent se) {
            HttpSessionImpl session = (HttpSessionImpl) se.getSession();
            final Session underlying;
            if (System.getSecurityManager() == null) {
                underlying = session.getSession();
            } else {
                underlying = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
            }
            List<WebSocketChannel> connections = (List<WebSocketChannel>) underlying.getAttribute(SESSION_ATTRIBUTE);
            if(connections != null) {
                synchronized (underlying) {
                    for(WebSocketChannel c : connections) {
                        WebSockets.sendClose(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "", c, null);
                    }
                }
            }
        }
    }

}
