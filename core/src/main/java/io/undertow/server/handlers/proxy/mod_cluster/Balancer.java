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
     * value: number of attempts to send the request to the backend server. Default: "1"
     */
    private final int maxattempts;

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
        this.maxattempts = b.getMaxattempts();
    }

    public int getId() {
        return id;
    }

    /**
     * Getter for name
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Getter for stickySession
     *
     * @return the stickySession
     */
    public boolean isStickySession() {
        return this.stickySession;
    }

    /**
     * Getter for stickySessionCookie
     *
     * @return the stickySessionCookie
     */
    public String getStickySessionCookie() {
        return this.stickySessionCookie;
    }

    /**
     * Getter for stickySessionPath
     *
     * @return the stickySessionPath
     */
    public String getStickySessionPath() {
        return this.stickySessionPath;
    }

    /**
     * Getter for stickySessionRemove
     *
     * @return the stickySessionRemove
     */
    public boolean isStickySessionRemove() {
        return this.stickySessionRemove;
    }

    /**
     * Getter for stickySessionForce
     *
     * @return the stickySessionForce
     */
    public boolean isStickySessionForce() {
        return this.stickySessionForce;
    }

    /**
     * Getter for waitWorker
     *
     * @return the waitWorker
     */
    public int getWaitWorker() {
        return this.waitWorker;
    }

    /**
     * Getter for maxattempts
     *
     * @return the maxattempts
     */
    public int getMaxattempts() {
        return this.maxattempts;
    }

    @Override
    public String toString() {
        return new StringBuilder("balancer: Name: ")
                .append(this.name).append(", Sticky: ").append(this.stickySession ? 1 : 0)
                .append(" [").append(this.stickySessionCookie).append("]/[")
                .append(this.stickySessionPath).append("], remove: ")
                .append(this.stickySessionRemove ? 1 : 0).append(", force: ")
                .append(this.stickySessionForce ? 1 : 0).append(", Timeout: ")
                .append(this.waitWorker).append(", Maxtry: ").append(this.maxattempts).toString();
    }

    static final BalancerBuilder builder() {
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
        private int maxattempts = 1;

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

        public int getMaxattempts() {
            return maxattempts;
        }

        public BalancerBuilder setMaxattempts(int maxattempts) {
            this.maxattempts = maxattempts;
            return this;
        }

        public Balancer build() {
            return new Balancer(this);
        }
    }
}
