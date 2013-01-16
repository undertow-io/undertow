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

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import java.util.Deque;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * An HTTP request handler which upgrades the HTTP request and hands it off as a socket to any XNIO consumer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelUpgradeHandler implements HttpHandler {
    private final CopyOnWriteMap<String, ChannelListener<? super ConnectedStreamChannel>> handlers = new CopyOnWriteMap<String, ChannelListener<? super ConnectedStreamChannel>>();
    private volatile HttpHandler nonUpgradeHandler;

    /**
     * Add a protocol to this handler.
     *
     * @param productString the product string to match
     * @param openListener the open listener to call
     * @return {@code true} if this product string was not previously registered, {@code false} otherwise
     */
    public boolean addProtocol(String productString, ChannelListener<? super ConnectedStreamChannel> openListener) {
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
    public ChannelListener<? super ConnectedStreamChannel> removeProtocol(String productString) {
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
    public void setNonUpgradeHandler(final HttpHandler nonUpgradeHandler) {
        this.nonUpgradeHandler = nonUpgradeHandler;
    }

    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final Deque<String> upgradeStrings = exchange.getRequestHeaders().get(Headers.UPGRADE);
        if (upgradeStrings != null) for (String string : upgradeStrings) {
            final ChannelListener<? super ConnectedStreamChannel> listener = handlers.get(string);
            if (listener != null) {
                exchange.upgradeChannel(string);
                ChannelListeners.invokeChannelListener(exchange.getConnection().getChannel(), listener);
                return;
            }
        }
        final HttpHandler handler = nonUpgradeHandler;
        if (handler == null) {
            exchange.setResponseCode(404);
            completionHandler.handleComplete();
        } else {
            HttpHandlers.executeHandler(handler, exchange, completionHandler);
        }
    }
}
