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

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.undertow.server.HttpHandler;

/**
 * @author Emanuel Muckenhuber
 */
public class MCMPConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static WebBuilder webBuilder() {
        return new WebBuilder();
    }

    private final String managementHost;
    private final String managementHostIp;
    private final int managementPort;
    private final AdvertiseConfig advertiseConfig;

    public MCMPConfig(Builder builder) {
        this.managementHost = builder.managementHost;
        this.managementPort = builder.managementPort;
        if (builder.advertiseBuilder != null) {
            this.advertiseConfig = new AdvertiseConfig(builder.advertiseBuilder, this);
        } else {
            this.advertiseConfig = null;
        }
        String mhip = managementHost;
        try {
            mhip = InetAddress.getByName(managementHost).getHostAddress();
        } catch (UnknownHostException e) {

        }
        this.managementHostIp = mhip;
    }

    public String getManagementHost() {
        return managementHost;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public String getManagementHostIp() {
        return managementHostIp;
    }

    AdvertiseConfig getAdvertiseConfig() {
        return advertiseConfig;
    }

    public HttpHandler create(final ModCluster modCluster, final HttpHandler next) {
        return new MCMPHandler(this, modCluster, next);
    }

    static class MCMPWebManagerConfig extends MCMPConfig {

        private final boolean allowCmd;
        private final boolean checkNonce;
        private final boolean reduceDisplay;
        private final boolean displaySessionids;

        MCMPWebManagerConfig(WebBuilder builder) {
            super(builder);
            this.allowCmd = builder.allowCmd;
            this.checkNonce = builder.checkNonce;
            this.reduceDisplay = builder.reduceDisplay;
            this.displaySessionids = builder.displaySessionids;
        }

        public boolean isAllowCmd() {
            return allowCmd;
        }

        public boolean isCheckNonce() {
            return checkNonce;
        }

        public boolean isReduceDisplay() {
            return reduceDisplay;
        }

        public boolean isDisplaySessionids() {
            return displaySessionids;
        }

        @Override
        public HttpHandler create(ModCluster modCluster, HttpHandler next) {
            return new MCMPWebManager(this, modCluster, next);
        }
    }

    static class AdvertiseConfig {

        private final String advertiseGroup;
        private final String advertiseAddress;
        private final int advertisePort;

        private final String securityKey;
        private final String protocol;
        private final String path;

        private final int advertiseFrequency;

        private final String managementHost;
        private final int managementPort;

        AdvertiseConfig(AdvertiseBuilder builder, MCMPConfig config) {
            this.advertiseGroup = builder.advertiseGroup;
            this.advertiseAddress = builder.advertiseAddress;
            this.advertiseFrequency = builder.advertiseFrequency;
            this.advertisePort = builder.advertisePort;
            this.securityKey = builder.securityKey;
            this.protocol = builder.protocol;
            this.path = builder.path;
            this.managementHost = config.getManagementHost();
            this.managementPort = config.getManagementPort();
        }

        public String getAdvertiseGroup() {
            return advertiseGroup;
        }

        public String getAdvertiseAddress() {
            return advertiseAddress;
        }

        public int getAdvertisePort() {
            return advertisePort;
        }

        public String getSecurityKey() {
            return securityKey;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getPath() {
            return path;
        }

        public int getAdvertiseFrequency() {
            return advertiseFrequency;
        }

        public String getManagementHost() {
            return managementHost;
        }

        public int getManagementPort() {
            return managementPort;
        }
    }

    public static class Builder {

        private String managementHost;
        private int managementPort;
        private AdvertiseBuilder advertiseBuilder;

        public Builder setManagementHost(String managementHost) {
            this.managementHost = managementHost;
            return this;
        }

        public Builder setManagementPort(int managementPort) {
            this.managementPort = managementPort;
            return this;
        }

        public AdvertiseBuilder enableAdvertise() {
            this.advertiseBuilder = new AdvertiseBuilder(this);
            return advertiseBuilder;
        }

        public MCMPConfig build() {
            return new MCMPConfig(this);
        }

        public HttpHandler create(final ModCluster modCluster, final HttpHandler next) {
            final MCMPConfig config = build();
            return config.create(modCluster, next);
        }

    }

    public static class WebBuilder extends Builder {

        boolean checkNonce = true;
        boolean reduceDisplay = false;
        boolean allowCmd = true;
        boolean displaySessionids = false;

        public WebBuilder setCheckNonce(boolean checkNonce) {
            this.checkNonce = checkNonce;
            return this;
        }

        public WebBuilder setReduceDisplay(boolean reduceDisplay) {
            this.reduceDisplay = reduceDisplay;
            return this;
        }

        public WebBuilder setAllowCmd(boolean allowCmd) {
            this.allowCmd = allowCmd;
            return this;
        }

        public WebBuilder setDisplaySessionids(boolean displaySessionids) {
            this.displaySessionids = displaySessionids;
            return this;
        }

        @Override
        public MCMPConfig build() {
            return new MCMPWebManagerConfig(this);
        }

    }

    public static class AdvertiseBuilder {

        String advertiseGroup = "224.0.1.105";
        String advertiseAddress = "127.0.0.1";
        int advertisePort = 23364;

        String securityKey;
        String protocol = "http";
        String path = "/";

        int advertiseFrequency = 10000;

        private final Builder parent;
        public AdvertiseBuilder(Builder parent) {
            this.parent = parent;
        }

        public AdvertiseBuilder setAdvertiseGroup(String advertiseGroup) {
            this.advertiseGroup = advertiseGroup;
            return this;
        }

        public AdvertiseBuilder setAdvertiseAddress(String advertiseAddress) {
            this.advertiseAddress = advertiseAddress;
            return this;
        }

        public AdvertiseBuilder setAdvertisePort(int advertisePort) {
            this.advertisePort = advertisePort;
            return this;
        }

        public AdvertiseBuilder setSecurityKey(String securityKey) {
            this.securityKey = securityKey;
            return this;
        }

        public AdvertiseBuilder setProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public AdvertiseBuilder setPath(String path) {
            if (path.startsWith("/")) {
                this.path = path;
            } else {
                this.path = "/" + path;
            }
            return this;
        }

        public AdvertiseBuilder setAdvertiseFrequency(int advertiseFrequency) {
            this.advertiseFrequency = advertiseFrequency;
            return this;
        }

        public Builder getParent() {
            return parent;
        }
    }

}
