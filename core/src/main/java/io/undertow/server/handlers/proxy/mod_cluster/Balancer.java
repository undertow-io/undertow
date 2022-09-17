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

import io.undertow.UndertowLogger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mod_cluster balancer config.
 *
 * @author Stuart Douglas
 * @author Emanuel Muckenhuber
 */
public class Balancer {

    /**
     * Name of the balancer. max size: 40, Default: "mycluster"
     */
    private final String name;

    /**
     * {@code true}: use JVMRoute to stick a request to a node, {@code false}: ignore JVMRoute. Default: {@code true}
     */
    private final boolean stickySession;

    /**
     * Name of the cookie containing the session-id. Max size: 30 Default: "JSESSIONID"
     */
    private final String stickySessionCookie;

    /**
     * Name of the parameter containing the session-id. Max size: 30. Default: "jsessionid"
     */
    private final String stickySessionPath;

    /**
     * {@code true}: remove the session-id (cookie or parameter) when the request can't be
     * routed to the right node. {@code false}: send it anyway. Default: {@code false}
     */
    private final boolean stickySessionRemove;

    /**
     * {@code true}: Return an error if the request can't be routed according to
     * JVMRoute, {@code false}: Route it to another node. Default: {@code true}
     */
    private final boolean stickySessionForce;

    /**
     * value in seconds: time to wait for an available worker. Default: "0" no wait.
     */
    private final int waitWorker;

    /**
     * Maximum number of failover attempts to send the request to the backend server. Default: "1"
     */
    private final int maxRetries;

    private final int id;
    private static final AtomicInteger idGen = new AtomicInteger();

    Balancer(BalancerBuilder b) {
        this.id = idGen.incrementAndGet();
        this.name = b.getName();
        this.stickySession = b.isStickySession();
        this.stickySessionCookie = b.getStickySessionCookie();
        this.stickySessionPath = b.getStickySessionPath();
        this.stickySessionRemove = b.isStickySessionRemove();
        this.stickySessionForce = b.isStickySessionForce();
        this.waitWorker = b.getWaitWorker();
        this.maxRetries = b.getMaxRetries();
        UndertowLogger.ROOT_LOGGER.balancerCreated(this.id, this.name, this.stickySession, this.stickySessionCookie, this.stickySessionPath,
                this.stickySessionRemove,  this.stickySessionForce, this.waitWorker, this.maxRetries);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return this.name;
    }

    public boolean isStickySession() {
        return this.stickySession;
    }

    public String getStickySessionCookie() {
        return this.stickySessionCookie;
    }

    public String getStickySessionPath() {
        return this.stickySessionPath;
    }

    public boolean isStickySessionRemove() {
        return this.stickySessionRemove;
    }

    public boolean isStickySessionForce() {
        return this.stickySessionForce;
    }

    public int getWaitWorker() {
        return this.waitWorker;
    }

    public int getMaxRetries() {
        return this.maxRetries;
    }

    @Deprecated
    public int getMaxattempts() {
        return this.maxRetries;
    }

    @Override
    public String toString() {
        return new StringBuilder("balancer: Name: ")
                .append(this.name).append(", Sticky: ").append(this.stickySession ? 1 : 0)
                .append(" [").append(this.stickySessionCookie).append("]/[")
                .append(this.stickySessionPath).append("], remove: ")
                .append(this.stickySessionRemove ? 1 : 0).append(", force: ")
                .append(this.stickySessionForce ? 1 : 0).append(", Timeout: ")
                .append(this.waitWorker).append(", Maxtry: ").append(this.maxRetries).toString();
    }

    static BalancerBuilder builder() {
        return new BalancerBuilder();
    }

    public static final class BalancerBuilder {

        private String name = "mycluster";
        private boolean stickySession = true;
        private String stickySessionCookie = "JSESSIONID";
        private String stickySessionPath = "jsessionid";
        private boolean stickySessionRemove = false;
        private boolean stickySessionForce = true;
        private int waitWorker = 0;
        private int maxRetries = 1;

        public String getName() {
            return name;
        }

        public BalancerBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public boolean isStickySession() {
            return stickySession;
        }

        public BalancerBuilder setStickySession(boolean stickySession) {
            this.stickySession = stickySession;
            return this;
        }

        public String getStickySessionCookie() {
            return stickySessionCookie;
        }

        public BalancerBuilder setStickySessionCookie(String stickySessionCookie) {
            if (stickySessionCookie != null && stickySessionCookie.length() > 30) {
                this.stickySessionCookie = stickySessionCookie.substring(0, 30);
                UndertowLogger.ROOT_LOGGER.stickySessionCookieLengthTruncated(stickySessionCookie, this.stickySessionCookie);
            } else {
                this.stickySessionCookie = stickySessionCookie;
            }
            return this;
        }

        public String getStickySessionPath() {
            return stickySessionPath;
        }

        public BalancerBuilder setStickySessionPath(String stickySessionPath) {
            this.stickySessionPath = stickySessionPath;
            return this;
        }

        public boolean isStickySessionRemove() {
            return stickySessionRemove;
        }

        public BalancerBuilder setStickySessionRemove(boolean stickySessionRemove) {
            this.stickySessionRemove = stickySessionRemove;
            return this;
        }

        public boolean isStickySessionForce() {
            return stickySessionForce;
        }

        public BalancerBuilder setStickySessionForce(boolean stickySessionForce) {
            this.stickySessionForce = stickySessionForce;
            return this;
        }

        public int getWaitWorker() {
            return waitWorker;
        }

        public BalancerBuilder setWaitWorker(int waitWorker) {
            this.waitWorker = waitWorker;
            return this;
        }

        public int getMaxRetries() {
            return this.maxRetries;
        }

        /**
         * Maximum number of failover attempts to send the request to the backend server.
         *
         * @param maxRetries number of failover attempts
         */
        public BalancerBuilder setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * @deprecated Use {@link BalancerBuilder#getMaxRetries()}.
         */
        @Deprecated(since="2.0.4", forRemoval=true)
        public int getMaxattempts() {
            return maxRetries;
        }

        /**
         * @deprecated Use {@link BalancerBuilder#setMaxRetries(int)}.
         */
        @Deprecated(since="2.0.4", forRemoval=true)
        public BalancerBuilder setMaxattempts(int maxattempts) {
            this.maxRetries = maxattempts;
            return this;
        }

        public Balancer build() {
            return new Balancer(this);
        }
    }
}
