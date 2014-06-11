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

package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.StreamConnection;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An HTTP request handler which upgrades the HTTP request and hands it off as a socket to any XNIO consumer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
public final class ChannelUpgradeHandler implements HttpHandler {
    private final CopyOnWriteMap<String, List<Holder>> handlers = new CopyOnWriteMap<>();
    private volatile HttpHandler nonUpgradeHandler = ResponseCodeHandler.HANDLE_404;

    /**
     * Add a protocol to this handler.
     *
     * @param productString the product string to match
     * @param openListener  the open listener to call
     * @param handshake     a handshake implementation that can be used to verify the client request and modify the response
     */
    public synchronized void addProtocol(String productString, ChannelListener<? super StreamConnection> openListener, final HttpUpgradeHandshake handshake) {
        if (productString == null) {
            throw new IllegalArgumentException("productString is null");
        }
        if (openListener == null) {
            throw new IllegalArgumentException("openListener is null");
        }
        List<Holder> list = handlers.get(productString);
        if (list == null) {
            handlers.put(productString, list = new CopyOnWriteArrayList<>());
        }
        list.add(new Holder(openListener, handshake));
    }

    /**
     * Add a protocol to this handler.
     *
     * @param productString the product string to match
     * @param openListener  the open listener to call
     */
    public void addProtocol(String productString, ChannelListener<? super StreamConnection> openListener) {
        addProtocol(productString, openListener, null);
    }

    /**
     * Remove a protocol from this handler. This will remove all upgrade handlers that match the product string
     *
     * @param productString the product string to match
     */
    public synchronized void removeProtocol(String productString) {
        handlers.remove(productString);
    }

    /**
     * Remove a protocol from this handler.
     *
     * @param productString the product string to match
     * @param openListener  The open listener
     */
    public synchronized void removeProtocol(String productString, ChannelListener<? super StreamConnection> openListener) {
        List<Holder> holders = handlers.get(productString);
        if (holders == null) {
            return;
        }
        Iterator<Holder> it = holders.iterator();
        while (it.hasNext()) {
            Holder holder = it.next();
            if (holder.listener == openListener) {
                it.remove();
                break;
            }
        }
        if (holders.isEmpty()) {
            handlers.remove(productString);
        }
    }

    /**
     * Get the non-upgrade delegate handler.
     *
     * @return the non-upgrade delegate handler
     */
    public HttpHandler getNonUpgradeHandler() {
        return nonUpgradeHandler;
    }

    /**
     * Set the non-upgrade delegate handler.
     *
     * @param nonUpgradeHandler the non-upgrade delegate handler
     */
    public ChannelUpgradeHandler setNonUpgradeHandler(final HttpHandler nonUpgradeHandler) {
        Handlers.handlerNotNull(nonUpgradeHandler);
        this.nonUpgradeHandler = nonUpgradeHandler;
        return this;
    }

    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final List<String> upgradeStrings = exchange.getRequestHeaders().get(Headers.UPGRADE);
        if (upgradeStrings != null && exchange.getRequestMethod().equals(Methods.GET)) {
            for (String string : upgradeStrings) {
                final List<Holder> holders = handlers.get(string);
                if (holders != null) {
                    for (Holder holder : holders) {
                        final ChannelListener<? super StreamConnection> listener = holder.listener;
                        if (holder.handshake != null) {
                            if (!holder.handshake.handleUpgrade(exchange)) {
                                //handshake did not match, try again
                                continue;
                            }
                        }

                        exchange.upgradeChannel(string, new HttpUpgradeListener() {

                            @Override
                            public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                                ChannelListeners.invokeChannelListener(streamConnection, listener);
                            }
                        });
                        exchange.endExchange();
                        return;
                    }
                }
            }
        }
        nonUpgradeHandler.handleRequest(exchange);
    }


    private static final class Holder {
        final ChannelListener<? super StreamConnection> listener;
        final HttpUpgradeHandshake handshake;

        private Holder(final ChannelListener<? super StreamConnection> listener, final HttpUpgradeHandshake handshake) {
            this.listener = listener;
            this.handshake = handshake;
        }
    }
}
