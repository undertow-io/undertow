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
package io.undertow.server.handlers.proxy;

import static org.xnio.IoUtils.safeClose;
import io.undertow.client.ClientConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.TimeUnit;

/**
 * An extension to {@link LoadBalancingProxyClient} that also allows the request to be taken into account to convert an open
 * connection for exclusive use.
 *
 * An existing connection can be taken to become exclusive but as soon as it is converted to be exclusive and associated with
 * the clients connection it will never be re-used. Once the client connection is closed so will the proxied connection.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class LoadBalancingProxyClientWithExclusivity extends LoadBalancingProxyClient {

    private static final AttachmentKey<ExclusiveConnectionHolder> CONNECTION_HOLDER_KEY = AttachmentKey.create(ExclusiveConnectionHolder.class);

    private final ExclusivityChecker checker;

    public LoadBalancingProxyClientWithExclusivity(final ExclusivityChecker checker) {
        this.checker = checker;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, long timeout,
            TimeUnit timeUnit) {
        final ExclusiveConnectionHolder holder = exchange.getConnection().getAttachment(CONNECTION_HOLDER_KEY);
        if (holder != null && holder.connection.getConnection().isOpen()) {
            // Something has already caused an exclusive connection to be allocated so keep using it.
            callback.completed(exchange, holder.connection);
        }

        final Host host = selectHost(exchange);
        if (host == null) {
            callback.failed(exchange);
        } else {
            if (holder != null || checker.isExclusivityRequired(exchange)) {
                // If we have a holder, even if the connection was closed we now exclusivity was already requested so our client
                // may be assuming it still exists.
                host.connect(target, exchange, new ProxyCallback<ProxyConnection>() {

                    @Override
                    public void failed(HttpServerExchange exchange) {
                        callback.failed(exchange);
                    }

                    @Override
                    public void completed(HttpServerExchange exchange, ProxyConnection result) {
                        if (holder != null) {
                            holder.connection = result;
                        } else {
                            final ExclusiveConnectionHolder newHolder = new ExclusiveConnectionHolder();
                            newHolder.connection = result;
                            ServerConnection connection = exchange.getConnection();
                            connection.putAttachment(CONNECTION_HOLDER_KEY, newHolder);
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
                }, timeout, timeUnit, true);
            } else {
                host.connect(target, exchange, callback, timeout, timeUnit, false);
            }
        }
    }

    public interface ExclusivityChecker {

        boolean isExclusivityRequired(HttpServerExchange exchange);

    }

    private class ExclusiveConnectionHolder {

        private ProxyConnection connection;

    }
}
