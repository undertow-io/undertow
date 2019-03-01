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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import static org.xnio.IoUtils.safeClose;

import java.util.concurrent.TimeUnit;

import io.undertow.client.ClientConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.ExclusivityChecker;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.AttachmentKey;

class ModClusterProxyClient implements ProxyClient {

    /**
     * The attachment key that is used to attach the proxy connection to the exchange.
     * <p>
     * This cannot be static as otherwise a connection from a different client could be re-used.
     */
    private final AttachmentKey<ExclusiveConnectionHolder> exclusiveConnectionKey = AttachmentKey
            .create(ExclusiveConnectionHolder.class);

    private final ExclusivityChecker exclusivityChecker;
    private final ModClusterContainer container;

    protected ModClusterProxyClient(ExclusivityChecker exclusivityChecker, ModClusterContainer container) {
        this.exclusivityChecker = exclusivityChecker;
        this.container = container;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return container.findTarget(exchange);
    }

    public void getConnection(final ProxyTarget target, final HttpServerExchange exchange,
                              final ProxyCallback<ProxyConnection> callback, final long timeout, final TimeUnit timeUnit) {
        final ExclusiveConnectionHolder holder = exchange.getConnection().getAttachment(exclusiveConnectionKey);
        if (holder != null && holder.connection.getConnection().isOpen()) {
            // Something has already caused an exclusive connection to be
            // allocated so keep using it.
            callback.completed(exchange, holder.connection);
            return;
        }
        if (! (target instanceof ModClusterProxyTarget)) {
            callback.couldNotResolveBackend(exchange);
            return;
        }

        // Resolve the node
        final ModClusterProxyTarget proxyTarget = (ModClusterProxyTarget) target;
        final Context context = proxyTarget.resolveContext(exchange);
        if (context == null) {
            callback.couldNotResolveBackend(exchange);
        } else {
            if (holder != null || (exclusivityChecker != null && exclusivityChecker.isExclusivityRequired(exchange))) {
                // If we have a holder, even if the connection was closed we now
                // exclusivity was already requested so our client
                // may be assuming it still exists.
                final ProxyCallback<ProxyConnection> wrappedCallback = new ProxyCallback<ProxyConnection>() {

                    @Override
                    public void completed(HttpServerExchange exchange, ProxyConnection result) {
                        if (holder != null) {
                            holder.connection = result;
                        } else {
                            final ExclusiveConnectionHolder newHolder = new ExclusiveConnectionHolder();
                            newHolder.connection = result;
                            ServerConnection connection = exchange.getConnection();
                            connection.putAttachment(exclusiveConnectionKey, newHolder);
                            connection.addCloseListener(new ServerConnection.CloseListener() {

                                @Override
                                public void closed(ServerConnection connection) {
                                    ClientConnection clientConnection = newHolder.connection.getConnection();
                                    if (clientConnection.isOpen()) {
                                        safeClose(clientConnection);
                                    }
                                }
                            });
                        }
                        callback.completed(exchange, result);
                    }

                    @Override
                    public void queuedRequestFailed(HttpServerExchange exchange) {
                        callback.queuedRequestFailed(exchange);
                    }

                    @Override
                    public void failed(HttpServerExchange exchange) {
                        callback.failed(exchange);
                    }

                    @Override
                    public void couldNotResolveBackend(HttpServerExchange exchange) {
                        callback.couldNotResolveBackend(exchange);
                    }
                };

                context.handleRequest(proxyTarget, exchange, wrappedCallback, timeout, timeUnit, true);
            } else {
                context.handleRequest(proxyTarget, exchange, callback, timeout, timeUnit, false);
            }
        }
    }

    private static class ExclusiveConnectionHolder {

        private ProxyConnection connection;

    }

}
