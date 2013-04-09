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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.jsr.handshake.HandshakeUtil;
import io.undertow.websockets.jsr.handshake.JsrHybi07Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi08Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi13Handshake;

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

    private final List<ConfiguredServerEndpoint> configuredServerEndpoints;

    private final Map<ConfiguredServerEndpoint, List<Handshake>> handshakes;

    private final WebSocketConnectionCallback callback;

    public JsrWebSocketFilter(WebSocketConnectionCallback callback, List<ConfiguredServerEndpoint> config) {
        this.callback = callback;
        List<ConfiguredServerEndpoint> endpoints = new ArrayList<>(config);
        Collections.sort(endpoints, new Comparator<ConfiguredServerEndpoint>() {
            @Override
            public int compare(final ConfiguredServerEndpoint o1, final ConfiguredServerEndpoint o2) {
                return o1.getPathTemplate().compareTo(o2.getPathTemplate());
            }
        });

        this.configuredServerEndpoints = endpoints;
        this.handshakes = handshakes(endpoints);
    }

    protected Map<ConfiguredServerEndpoint, List<Handshake>> handshakes(List<ConfiguredServerEndpoint> configs) {
        final IdentityHashMap<ConfiguredServerEndpoint, List<Handshake>> ret = new IdentityHashMap<>();
        for (ConfiguredServerEndpoint config : configs) {
            List<Handshake> handshakes = new ArrayList<>();
            handshakes.add(new JsrHybi13Handshake(config));
            handshakes.add(new JsrHybi08Handshake(config));
            handshakes.add(new JsrHybi07Handshake(config));
            ret.put(config, handshakes);
        }
        return ret;
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (req.getHeader("Upgrade") != null) {
            final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp);

            String path;
            if (req.getPathInfo() == null) {
                path = req.getServletPath();
            } else {
                path = req.getServletPath() + req.getPathInfo();
            }
            if(!path.startsWith("/")) {
                path = "/" + path;
            }

            final Map<String, String> params = new HashMap<>();
            //we need a better way of handling this mapping.
            for (ConfiguredServerEndpoint endpoint : configuredServerEndpoints) {
                if (endpoint.getPathTemplate().matches(path, params)) {
                    Handshake handshaker = null;
                    for (Handshake method : handshakes.get(endpoint)) {
                        if (method.matches(facade)) {
                            handshaker = method;
                            break;
                        }
                    }

                    if (handshaker == null) {
                        chain.doFilter(request, response);
                    } else {
                        facade.putAttachment(HandshakeUtil.PATH_PARAMS, params);
                        handshaker.handshake(facade, callback);
                        return;
                    }
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
}
