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

package io.undertow.server.handlers;

import java.util.List;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.StreamConnection;

/**
 * An HTTP request handler which upgrades the HTTP request and hands it off as a socket to any XNIO consumer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelUpgradeHandler implements HttpHandler {
    private final CopyOnWriteMap<String, ChannelListener<? super StreamConnection>> handlers = new CopyOnWriteMap<String, ChannelListener<? super StreamConnection>>();
    private volatile HttpHandler nonUpgradeHandler = ResponseCodeHandler.HANDLE_404;

    /**
     * Add a protocol to this handler.
     *
     * @param productString the product string to match
     * @param openListener  the open listener to call
     * @return {@code true} if this product string was not previously registered, {@code false} otherwise
     */
    public boolean addProtocol(String productString, ChannelListener<? super StreamConnection> openListener) {
        if (productString == null) {
            throw new IllegalArgumentException("productString is null");
        }
        if (openListener == null) {
            throw new IllegalArgumentException("openListener is null");
        }
        return handlers.putIfAbsent(productString, openListener) == null;
    }

    /**
     * Remove a protocol from this handler.
     *
     * @param productString the product string to match
     * @return the previously registered open listener, or {@code null} if none was registered
     */
    public ChannelListener<? super StreamConnection> removeProtocol(String productString) {
        return handlers.remove(productString);
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
        HttpHandlers.handlerNotNull(nonUpgradeHandler);
        this.nonUpgradeHandler = nonUpgradeHandler;
        return this;
    }

    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final List<String> upgradeStrings = exchange.getRequestHeaders().get(Headers.UPGRADE);
        if (upgradeStrings != null && exchange.getRequestMethod().equals(Methods.GET)) {
            for (String string : upgradeStrings) {
                final ChannelListener<? super StreamConnection> listener = handlers.get(string);
                if (listener != null) {
                    exchange.upgradeChannel(string, new ExchangeCompletionListener() {
                        @Override
                        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                            ChannelListeners.invokeChannelListener(exchange.getConnection().getChannel(), listener);
                        }
                    });
                    exchange.endExchange();
                    return;
                }
            }
        }
        final HttpHandler handler = nonUpgradeHandler;
        HttpHandlers.executeHandler(handler, exchange);
    }
}
