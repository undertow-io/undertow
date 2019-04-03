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
import java.util.List;
import java.util.function.Consumer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.websocket.CloseReason;
import javax.websocket.server.ServerContainer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.UndertowLogger;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.PathTemplateMatcher;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.jsr.handshake.Handshake;
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

    private EndpointSessionHandler callback;
    private PathTemplateMatcher<WebSocketHandshakeHolder> pathTemplateMatcher;
    private ServerWebSocketContainer container;

    private static final String SESSION_ATTRIBUTE = "io.undertow.websocket.current-connections";


    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        container = (ServerWebSocketContainer) filterConfig.getServletContext().getAttribute(ServerContainer.class.getName());
        container.deploymentComplete();
        pathTemplateMatcher = new PathTemplateMatcher<>();
        WebSocketDeploymentInfo info = (WebSocketDeploymentInfo) filterConfig.getServletContext().getAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME);
        for (ConfiguredServerEndpoint endpoint : container.getConfiguredServerEndpoints()) {
            if (info == null || info.getServerExtensions().isEmpty()) {
                pathTemplateMatcher.add(endpoint.getPathTemplate(), ServerWebSocketContainer.handshakes(endpoint));
            } else {
                pathTemplateMatcher.add(endpoint.getPathTemplate(), ServerWebSocketContainer.handshakes(endpoint, info.getServerExtensions()));
            }
        }
        this.callback = new EndpointSessionHandler(container);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (req.getHeader(HttpHeaderNames.UPGRADE) != null) {
            final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp);

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
                    if (container.isClosed()) {
                        resp.sendError(StatusCodes.SERVICE_UNAVAILABLE);
                        return;
                    }
                    facade.putAttachment(HandshakeUtil.PATH_PARAMS, matchResult.getParameters());
                    facade.putAttachment(HandshakeUtil.PRINCIPAL, req.getUserPrincipal());
                    final Handshake selected = handshaker;
                    ServletRequestContext src = ServletRequestContext.requireCurrent();
                    final HttpSessionImpl session = src.getCurrentServletContext().getSession(src.getExchange(), false);
                    handshaker.handshake(facade, new Consumer<ChannelHandlerContext>() {
                        @Override
                        public void accept(ChannelHandlerContext context) {
                            UndertowSession channel = callback.connected(context, selected.getConfig(), facade, src.getOriginalResponse().getHeader(io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL.toString()));
                            if (session != null && channel != null) {
                                final Session underlying;
                                if (System.getSecurityManager() == null) {
                                    underlying = session.getSession();
                                } else {
                                    underlying = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
                                }
                                List<UndertowSession> connections;
                                synchronized (underlying) {
                                    connections = (List<UndertowSession>) underlying.getAttribute(SESSION_ATTRIBUTE);
                                    if (connections == null) {
                                        underlying.setAttribute(SESSION_ATTRIBUTE, connections = new ArrayList<>());
                                    }
                                    connections.add(channel);
                                }
                                final List<UndertowSession> finalConnections = connections;
                                context.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
                                    @Override
                                    public void operationComplete(Future<? super Void> future) throws Exception {
                                        synchronized (underlying) {
                                            finalConnections.remove(channel);
                                        }
                                    }
                                });
                            }
                        }
                    });
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
            List<UndertowSession> connections = (List<UndertowSession>) underlying.getAttribute(SESSION_ATTRIBUTE);
            if (connections != null) {
                synchronized (underlying) {
                    for (UndertowSession c : connections) {
                        try {
                            c.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, ""));
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        }
                    }
                }
            }
        }
    }

}
