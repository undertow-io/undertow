/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
import io.undertow.server.HttpUpgradeListener;
import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatcher;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.jsr.handshake.HandshakeUtil;
import io.undertow.websockets.jsr.handshake.JsrHybi07Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi08Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi13Handshake;
import org.xnio.StreamConnection;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerContainer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Filter that provides HTTP upgrade functionality. This should be run after all user filters, but before any servlets.
 * <p/>
 * The use of a filter rather than a servlet allows for normal HTTP requests to be served from the same location
 * as a web socket endpoint if no upgrade header is found.
 * <p/>
 * TODO: this needs a lot of work
 *
 * @author Stuart Douglas
 */
public class JsrWebSocketFilter implements Filter {

    private WebSocketConnectionCallback callback;
    private PathTemplateMatcher<WebSocketHandshakeHolder> pathTemplateMatcher;

    protected WebSocketHandshakeHolder handshakes(ConfiguredServerEndpoint config) {
        List<Handshake> handshakes = new ArrayList<Handshake>();
        handshakes.add(new JsrHybi13Handshake(config));
        handshakes.add(new JsrHybi08Handshake(config));
        handshakes.add(new JsrHybi07Handshake(config));
        return new WebSocketHandshakeHolder(handshakes, config);
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        ServerWebSocketContainer container = (ServerWebSocketContainer) filterConfig.getServletContext().getAttribute(ServerContainer.class.getName());
        container.deploymentComplete();
        pathTemplateMatcher = new PathTemplateMatcher<WebSocketHandshakeHolder>();
        for (ConfiguredServerEndpoint endpoint : container.getConfiguredServerEndpoints()) {
            pathTemplateMatcher.add(endpoint.getPathTemplate(), handshakes(endpoint));
        }
        this.callback = new EndpointSessionHandler(container);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (req.getHeader(Headers.UPGRADE_STRING) != null) {
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

                if (handshaker == null) {
                    chain.doFilter(request, response);
                } else {
                    facade.putAttachment(HandshakeUtil.PATH_PARAMS, matchResult.getParameters());
                    final Handshake selected = handshaker;
                    facade.upgradeChannel(new HttpUpgradeListener() {
                        @Override
                        public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                            WebSocketChannel channel = selected.createChannel(facade, streamConnection, facade.getBufferPool());
                            callback.onConnect(facade, channel);
                        }
                    });
                    handshaker.handshake(facade);
                    return;
                }
            }
            chain.doFilter(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }

    private static final class WebSocketHandshakeHolder {
        final List<Handshake> handshakes;
        final ConfiguredServerEndpoint endpoint;

        private WebSocketHandshakeHolder(List<Handshake> handshakes, ConfiguredServerEndpoint endpoint) {
            this.handshakes = handshakes;
            this.endpoint = endpoint;
        }
    }
}
