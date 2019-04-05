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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.netty.channel.ChannelHandlerContext;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.HttpMethodNames;

/**
 * An HTTP request handler which upgrades the HTTP request and hands it off as a socket to any XNIO consumer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
public final class ChannelUpgradeHandler implements HttpHandler {
    private final CopyOnWriteMap<String, List<Holder>> handlers = new CopyOnWriteMap<>();
    private volatile HttpHandler nonUpgradeHandler = ResponseCodeHandler.HANDLE_404;

    public synchronized void addProtocol(String productString, Consumer<ChannelHandlerContext> openListener, final HttpUpgradeHandshake handshake) {
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
    public void addProtocol(String productString, Consumer<ChannelHandlerContext> openListener) {
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
     * @param productString   the product string to match
     * @param upgradeListener The upgrade listener
     */
    public synchronized void removeProtocol(String productString, Consumer<ChannelHandlerContext> upgradeListener) {
        List<Holder> holders = handlers.get(productString);
        if (holders == null) {
            return;
        }
        Iterator<Holder> it = holders.iterator();
        while (it.hasNext()) {
            Holder holder = it.next();
            if (holder.listener == upgradeListener) {
                holders.remove(holder);
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
        final List<String> upgradeStrings = exchange.requestHeaders().getAll(HttpHeaderNames.UPGRADE);
        if (upgradeStrings != null && exchange.requestMethod().equals(HttpMethodNames.GET)) {
            for (String string : upgradeStrings) {
                final List<Holder> holders = handlers.get(string);
                if (holders != null) {
                    for (Holder holder : holders) {
                        final Consumer<ChannelHandlerContext> listener = holder.listener;
                        if (holder.handshake != null) {
                            if (!holder.handshake.handleUpgrade(exchange)) {
                                //handshake did not match, try again
                                continue;
                            }
                        }

                        exchange.upgradeChannel(string, listener);
                        exchange.endExchange();
                        return;
                    }
                }
            }
        }
        nonUpgradeHandler.handleRequest(exchange);
    }

    private static final class Holder {
        final Consumer<ChannelHandlerContext> listener;
        final HttpUpgradeHandshake handshake;

        private Holder(final Consumer<ChannelHandlerContext> listener, final HttpUpgradeHandshake handshake) {
            this.listener = listener;
            this.handshake = handshake;
        }
    }
}
