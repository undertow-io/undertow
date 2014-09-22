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

import io.undertow.server.handlers.PathHandler;

/**
 * Unit test configuration for a node.
 *
 * @author Emanuel Muckenhuber
 */
class NodeTestConfig implements Cloneable {

    private String jvmRoute;
    private String domain;

    private String type;
    private String hostname;
    private Integer port;

    private Boolean flushPackets;
    private Integer flushwait;
    private Integer ping;
    private Integer smax;
    private Integer ttl;
    private Integer timeout;

    private String BalancerName;
    private Boolean stickySession;
    private String stickySessionCookie;
    private String stickySessionPath;
    private Boolean stickySessionRemove;
    private Boolean stickySessionForce;
    private Integer waitWorker;
    private Integer maxattempts;

    private NodeTestHandlers testHandlers;

    static NodeTestConfig builder() {
        return new NodeTestConfig();
    }

    public String getBalancerName() {
        return BalancerName;
    }

    public NodeTestConfig setBalancerName(String balancerName) {
        this.BalancerName = balancerName;
        return this;
    }

    public Boolean getStickySession() {
        return stickySession;
    }

    public NodeTestConfig setStickySession(Boolean stickySession) {
        this.stickySession = stickySession;
        return this;
    }

    public String getStickySessionCookie() {
        return stickySessionCookie;
    }

    public NodeTestConfig setStickySessionCookie(String stickySessionCookie) {
        this.stickySessionCookie = stickySessionCookie;
        return this;
    }

    public String getStickySessionPath() {
        return stickySessionPath;
    }

    public NodeTestConfig setStickySessionPath(String stickySessionPath) {
        this.stickySessionPath = stickySessionPath;
        return this;
    }

    public Boolean getStickySessionRemove() {
        return stickySessionRemove;
    }

    public NodeTestConfig setStickySessionRemove(Boolean stickySessionRemove) {
        this.stickySessionRemove = stickySessionRemove;
        return this;
    }

    public Boolean getStickySessionForce() {
        return stickySessionForce;
    }

    public NodeTestConfig setStickySessionForce(Boolean stickySessionForce) {
        this.stickySessionForce = stickySessionForce;
        return this;
    }

    public Integer getWaitWorker() {
        return waitWorker;
    }

    public NodeTestConfig setWaitWorker(Integer waitWorker) {
        this.waitWorker = waitWorker;
        return this;
    }

    public Integer getMaxattempts() {
        return maxattempts;
    }

    public NodeTestConfig setMaxattempts(Integer maxattempts) {
        this.maxattempts = maxattempts;
        return this;
    }

    public String getJvmRoute() {
        return jvmRoute;
    }

    public NodeTestConfig setJvmRoute(String jvmRoute) {
        this.jvmRoute = jvmRoute;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public NodeTestConfig setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getType() {
        return type;
    }

    public NodeTestConfig setType(String type) {
        this.type = type;
        return this;
    }

    public String getHostname() {
        return hostname;
    }

    public NodeTestConfig setHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public NodeTestConfig setPort(Integer port) {
        this.port = port;
        return this;
    }

    public Boolean getFlushPackets() {
        return flushPackets;
    }

    public NodeTestConfig setFlushPackets(Boolean flushPackets) {
        this.flushPackets = flushPackets;
        return this;
    }

    public Integer getFlushwait() {
        return flushwait;
    }

    public NodeTestConfig setFlushwait(Integer flushwait) {
        this.flushwait = flushwait;
        return this;
    }

    public Integer getPing() {
        return ping;
    }

    public NodeTestConfig setPing(Integer ping) {
        this.ping = ping;
        return this;
    }

    public Integer getSmax() {
        return smax;
    }

    public NodeTestConfig setSmax(Integer smax) {
        this.smax = smax;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public NodeTestConfig setTtl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public NodeTestConfig setTimeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public NodeTestHandlers getTestHandlers() {
        return testHandlers;
    }

    public NodeTestConfig setTestHandlers(NodeTestHandlers testHandlers) {
        this.testHandlers = testHandlers;
        return this;
    }

    void setupHandlers(final PathHandler pathHandler) {
        if (testHandlers != null) {
            testHandlers.setup(pathHandler, this);
        }
    }

    @Override
    protected NodeTestConfig clone()  {
        try {
            return (NodeTestConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
