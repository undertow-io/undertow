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

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.Deque;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;

/**
 * An HTTP request handler which upgrades the HTTP request and hands it off as a socket to any XNIO consumer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelUpgradeHandler implements HttpHandler {
    private final CopyOnWriteMap<String, ChannelListener<? super ConnectedStreamChannel>> handlers = new CopyOnWriteMap<String, ChannelListener<? super ConnectedStreamChannel>>();
    private volatile HttpHandler nonUpgradeHandler = ResponseCodeHandler.HANDLE_404;

    /**
     * Add a protocol to this handler.
     *
     * @param productString the product string to match
     * @param openListener  the open listener to call
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
        HttpHandlers.handlerNotNull(nonUpgradeHandler);
        this.nonUpgradeHandler = nonUpgradeHandler;
    }

    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final Deque<String> upgradeStrings = exchange.getRequestHeaders().get(Headers.UPGRADE);
        if (upgradeStrings != null && exchange.getRequestMethod().equals(Methods.GET)) {
            for (String string : upgradeStrings) {
                final ChannelListener<? super ConnectedStreamChannel> listener = handlers.get(string);
                if (listener != null) {
                    try {
                        exchange.upgradeChannel(string);
                        exchange.getRequestChannel().shutdownReads();
                        final StreamSinkChannel sinkChannel = exchange.getResponseChannelFactory().create();
                        sinkChannel.shutdownWrites();
                        if (!sinkChannel.flush()) {
                            sinkChannel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(new ChannelListener<Channel>() {
                                public void handleEvent(final Channel channel) {
                                    ChannelListeners.invokeChannelListener(exchange.getConnection().getChannel(), listener);
                                }
                            }, null));
                            sinkChannel.resumeWrites();
                        } else {
                            ChannelListeners.invokeChannelListener(exchange.getConnection().getChannel(), listener);
                        }
                        return;
                    } catch (IOException e) {
                        completionHandler.handleComplete();
                        UndertowLogger.REQUEST_LOGGER.debug("Exception handling request", e);
                    }
                }
            }
        }
        final HttpHandler handler = nonUpgradeHandler;
        HttpHandlers.executeHandler(handler, exchange, completionHandler);
    }
}
