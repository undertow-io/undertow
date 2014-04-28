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

package io.undertow.websockets.jsr;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import javax.websocket.Endpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.util.PathTemplate;

/**
 * @author Stuart Douglas
 */
public class ConfiguredServerEndpoint {

    private final ServerEndpointConfig endpointConfiguration;
    private final InstanceFactory<Endpoint> endpointFactory;
    private final PathTemplate pathTemplate;
    private final EncodingFactory encodingFactory;
    private final Set<Session> openSessions = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<Session, Boolean>()));

    public ConfiguredServerEndpoint(final ServerEndpointConfig endpointConfiguration, final InstanceFactory<Endpoint> endpointFactory, final PathTemplate pathTemplate, final EncodingFactory encodingFactory) {
        this.endpointConfiguration = endpointConfiguration;
        this.endpointFactory = endpointFactory;
        this.pathTemplate = pathTemplate;
        this.encodingFactory = encodingFactory;
    }

    public ServerEndpointConfig getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public InstanceFactory<Endpoint> getEndpointFactory() {
        return endpointFactory;
    }

    public PathTemplate getPathTemplate() {
        return pathTemplate;
    }

    public EncodingFactory getEncodingFactory() {
        return encodingFactory;
    }

    public Set<Session> getOpenSessions() {
        synchronized (openSessions) {
            return new HashSet<Session>(openSessions);
        }
    }
}
