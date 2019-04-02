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

import java.util.Iterator;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyClient;

/**
 * @author Emanuel Muckenhuber
 * @author Radoslav Husar
 */
public interface ModClusterProxyTarget extends ProxyClient.ProxyTarget, ProxyClient.MaxRetriesProxyTarget {

    /**
     * Resolve the responsible context handling this request.
     *
     * @param exchange the http server exchange
     * @return the context
     */
    Context resolveContext(HttpServerExchange exchange);

    class ExistingSessionTarget implements ModClusterProxyTarget {

        private final String session;
        private final Iterator<CharSequence> routes;
        private final VirtualHost.HostEntry entry;
        private final boolean forceStickySession;
        private final ModClusterContainer container;

        private boolean resolved;
        private Context resolvedContext;

        public ExistingSessionTarget(String session, Iterator<CharSequence> routes, VirtualHost.HostEntry entry, ModClusterContainer container, boolean forceStickySession) {
            this.session = session;
            this.routes = routes;
            this.entry = entry;
            this.container = container;
            this.forceStickySession = forceStickySession;
        }

        @Override
        public Context resolveContext(HttpServerExchange exchange) {
            resolveContextIfUnresolved();

            return resolvedContext;
        }

        void resolveContextIfUnresolved() {
            if (resolved) return;

            resolved = true;
            boolean firstResolved = false;
            String firstRoute = null;
            String firstRouteDomain = null;

            while (routes.hasNext()) {
                final String jvmRoute = routes.next().toString();

                final Context context = entry.getContextForNode(jvmRoute);
                if (context != null && context.checkAvailable(true)) {
                    final Node node = context.getNode();
                    node.elected(); // Maybe move this to context#handleRequest
                    this.resolvedContext = context;
                    return;
                }

                if (!firstResolved) {
                    firstResolved = true;
                    firstRoute = jvmRoute;
                    firstRouteDomain = context != null ? context.getNode().getNodeConfig().getDomain() : null;
                }
            }

            this.resolvedContext = container.findFailoverNode(entry, firstRouteDomain, session, firstRoute, forceStickySession);
        }

        @Override
        public int getMaxRetries() {
            resolveContextIfUnresolved();

            if (resolvedContext == null) {
                return 0;
            }
            Balancer balancer = resolvedContext.getNode().getBalancer();
            if(balancer == null) {
                return 0;
            }
            return balancer.getMaxRetries();
        }
    }

    class BasicTarget implements ModClusterProxyTarget {

        private final VirtualHost.HostEntry entry;
        private final ModClusterContainer container;
        private Context resolved;

        public BasicTarget(VirtualHost.HostEntry entry, ModClusterContainer container) {
            this.entry = entry;
            this.container = container;
        }

        @Override
        public int getMaxRetries() {
            if(resolved == null) {
                resolveNode();
            }
            if(resolved == null) {
                return 0;
            }
            Balancer balancer = resolved.getNode().getBalancer();
            if(balancer == null) {
                return 0;
            }
            return balancer.getMaxRetries();
        }

        @Override
        public Context resolveContext(HttpServerExchange exchange) {
            if(resolved == null) {
                resolveNode();
            }
            return resolved;
        }

        private void resolveNode() {
            this.resolved = container.findNewNode(entry);
        }
    }

}
