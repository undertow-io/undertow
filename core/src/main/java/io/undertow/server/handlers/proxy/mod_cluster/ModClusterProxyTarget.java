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

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyClient;

/**
 * @author Emanuel Muckenhuber
 */
public interface ModClusterProxyTarget extends ProxyClient.ProxyTarget {

    /**
     * Resolve the responsible context handling this request.
     *
     * @param exchange the http server exchange
     * @return the context
     */
    Context resolveContext(HttpServerExchange exchange);

    class ExistingSessionTarget implements ModClusterProxyTarget {

        private final String jvmRoute;
        private final VirtualHost.HostEntry entry;
        private final boolean forceStickySession;
        private final ModClusterContainer container;

        public ExistingSessionTarget(String jvmRoute, VirtualHost.HostEntry entry, ModClusterContainer container, boolean forceStickySession) {
            this.jvmRoute = jvmRoute;
            this.entry = entry;
            this.container = container;
            this.forceStickySession = forceStickySession;
        }

        @Override
        public Context resolveContext(HttpServerExchange exchange) {
            final Context context = entry.getContextForNode(jvmRoute);
            if (context != null && context.checkAvailable(true)) {
                final Node node = context.getNode();
                node.elected(); // Maybe move this to context#handleRequest
                return context;
            }
            final String domain = context != null ? context.getNode().getNodeConfig().getDomain() : null;
            return container.findFailoverNode(entry, domain, jvmRoute, forceStickySession);
        }
    }

    class BasicTarget implements ModClusterProxyTarget {

        private final VirtualHost.HostEntry entry;
        private final ModClusterContainer container;

        public BasicTarget(VirtualHost.HostEntry entry, ModClusterContainer container) {
            this.entry = entry;
            this.container = container;
        }

        @Override
        public Context resolveContext(HttpServerExchange exchange) {
            return container.findNewNode(entry);
        }
    }

}
