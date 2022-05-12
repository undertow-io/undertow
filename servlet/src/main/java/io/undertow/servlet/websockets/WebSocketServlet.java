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

package io.undertow.servlet.websockets;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import io.undertow.websockets.core.protocol.version08.Hybi08Handshake;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import org.xnio.StreamConnection;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class WebSocketServlet extends HttpServlet {

    public static final String SESSION_HANDLER = "io.undertow.handler";

    private final List<Handshake> handshakes;

    private WebSocketConnectionCallback callback;

    private Set<WebSocketChannel> peerConnections;

    public WebSocketServlet() {
        this.handshakes = handshakes();
    }

    public WebSocketServlet(WebSocketConnectionCallback callback) {
        this.callback = callback;
        this.handshakes = handshakes();
    }


    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        peerConnections = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketChannel, Boolean>());
        try {
            final String sessionHandler = config.getInitParameter(SESSION_HANDLER);
            if (sessionHandler != null) {
                final Class<?> clazz = Class.forName(sessionHandler, true, Thread.currentThread().getContextClassLoader());
                final Object handler = clazz.newInstance();
                this.callback = (WebSocketConnectionCallback) handler;
            }
            //TODO: set properties based on init params

        } catch (ClassNotFoundException e) {
            throw new ServletException(e);
        } catch (InstantiationException e) {
            throw new ServletException(e);
        } catch (IllegalAccessException e) {
            throw new ServletException(e);
        }
        if (callback == null) {
            throw UndertowServletMessages.MESSAGES.noWebSocketHandler();
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp, peerConnections);
        Handshake handshaker = null;
        for (Handshake method : handshakes) {
            if (method.matches(facade)) {
                handshaker = method;
                break;
            }
        }

        if (handshaker == null) {
            UndertowLogger.REQUEST_LOGGER.debug("Could not find hand shaker for web socket request");
            resp.sendError(StatusCodes.BAD_REQUEST);
            return;
        }
        final Handshake selected = handshaker;
        facade.upgradeChannel(new HttpUpgradeListener() {
            @Override
            public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                WebSocketChannel channel = selected.createChannel(facade, streamConnection, facade.getBufferPool());
                peerConnections.add(channel);
                callback.onConnect(facade, channel);
            }
        });
        handshaker.handshake(facade);
    }

    protected List<Handshake> handshakes() {
        List<Handshake> handshakes = new ArrayList<>();
        handshakes.add(new Hybi13Handshake());
        handshakes.add(new Hybi08Handshake());
        handshakes.add(new Hybi07Handshake());
        return handshakes;
    }

}
