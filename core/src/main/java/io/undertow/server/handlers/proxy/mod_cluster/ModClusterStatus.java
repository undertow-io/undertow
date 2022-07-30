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

package io.undertow.server.handlers.proxy.mod_cluster;

import java.net.URI;
import java.util.List;

/**
 * An interface that allows the current status of the mod_cluster container to be queried and modified
 *
 * @author Stuart Douglas
 */
public interface ModClusterStatus {

    List<LoadBalancer> getLoadBalancers();

    LoadBalancer getLoadBalancer(String name);

    interface LoadBalancer {

        String getName();

        List<Node> getNodes();

        Node getNode(String name);

        boolean isStickySession();

        String getStickySessionCookie();

        String getStickySessionPath();

        boolean isStickySessionRemove();

        boolean isStickySessionForce();

        int getWaitWorker();

        /**
         * Returns maximum number of failover attempts to send the request to the backend server.
         *
         * @return number of failover attempts
         */
        int getMaxRetries();

        /**
         * @deprecated Use {@link LoadBalancer#getMaxRetries()}.
         */
        @Deprecated(since="2.0.4", forRemoval=true)
        int getMaxAttempts();
    }

    interface Node {

        String getName();

        URI getUri();

        List<Context> getContexts();

        Context getContext(String name);

        int getLoad();

        NodeStatus getStatus();

        int getOpenConnections();

        long getTransferred();

        long getRead();

        int getElected();

        int getCacheConnections();

        String getJvmRoute();

        String getDomain();

        int getFlushWait();

        int getMaxConnections();

        int getPing();

        int getRequestQueueSize();

        int getTimeout();

        long getTtl();

        boolean isFlushPackets();

        boolean isQueueNewRequests();

        List<String> getAliases();

        void resetStatistics();
    }

    interface Context {

        String getName();

        boolean isEnabled();

        boolean isStopped();

        int getRequests();

        void enable();

        void disable();

        void stop();
    }
}
