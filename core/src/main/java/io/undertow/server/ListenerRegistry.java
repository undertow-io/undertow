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

package io.undertow.server;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import io.undertow.UndertowMessages;
import io.undertow.util.CopyOnWriteMap;

/**
 * A registry of listeners, and the services that are exposed via these listeners.
 *
 * This is not used directly by Undertow, but can be used by embedding applications to
 * track listener metadata.
 *
 * @author Stuart Douglas
 */
public class ListenerRegistry {

    private final ConcurrentMap<String, Listener> listeners = new CopyOnWriteMap<>();

    public Listener getListener(final String name) {
        return listeners.get(name);
    }

    public void addListener(final Listener listener) {
        if(listeners.putIfAbsent(listener.getName(), listener) != null) {
            throw UndertowMessages.MESSAGES.listenerAlreadyRegistered(listener.getName());
        }
    }

    public void removeListener(final String name) {
        listeners.remove(name);
    }

    public static final class Listener {

        private final String protocol;
        private final String name;
        private final String serverName;
        private final InetSocketAddress bindAddress;

        /**
         * Map that can be used to store additional listener metadata
         */
        private final Map<String, Object> contextInformation = new CopyOnWriteMap<>();

        /**
         * Information about any HTTP upgrade handlers that are registered on this handler.
         */
        private final Set<HttpUpgradeMetadata> httpUpgradeMetadata = new CopyOnWriteArraySet<>();

        public Listener(final String protocol, final String name, final String serverName, final InetSocketAddress bindAddress) {
            this.protocol = protocol;
            this.name = name;
            this.serverName = serverName;
            this.bindAddress = bindAddress;
        }

        /**
         * The protocol that this listener is using
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * The optional listener name;
         */
        public String getName() {
            return name;
        }

        /**
         * The server name
         */
        public String getServerName() {
            return serverName;
        }

        /**
         * The address that this listener is bound to
         */
        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public Collection<String> getContextKeys() {
            return contextInformation.keySet();
        }

        public Object removeContextInformation(final String key) {
            return contextInformation.remove(key);
        }

        public Object setContextInformation(final String key, final Object value) {
            return contextInformation.put(key, value);
        }

        public Object getContextInformation(final String key) {
            return contextInformation.get(key);
        }

        public void addHttpUpgradeMetadata(final HttpUpgradeMetadata upgradeMetadata) {
            httpUpgradeMetadata.add(upgradeMetadata);
        }

        public void removeHttpUpgradeMetadata(final HttpUpgradeMetadata upgradeMetadata) {
            httpUpgradeMetadata.remove(upgradeMetadata);
        }

        public Set<HttpUpgradeMetadata> getHttpUpgradeMetadata() {
            return Collections.unmodifiableSet(httpUpgradeMetadata);
        }
    }

    public static final class HttpUpgradeMetadata {

        private final String protocol;
        private final String subProtocol;
        private final Map<String, Object> contextInformation = new CopyOnWriteMap<>();


        public HttpUpgradeMetadata(final String protocol, final String subProtocol) {
            this.protocol = protocol;
            this.subProtocol = subProtocol;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getSubProtocol() {
            return subProtocol;
        }

        public Collection<String> getContextKeys() {
            return contextInformation.keySet();
        }

        public Object removeContextInformation(final String key) {
            return contextInformation.remove(key);
        }

        public Object setContextInformation(final String key, final Object value) {
            return contextInformation.put(key, value);
        }

        public Object getContextInformation(final String key) {
            return contextInformation.get(key);
        }
    }

}
