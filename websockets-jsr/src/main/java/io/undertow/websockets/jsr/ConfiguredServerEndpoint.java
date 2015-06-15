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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.util.PathTemplate;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;

import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class ConfiguredServerEndpoint {

    private final ServerEndpointConfig endpointConfiguration;
    private final AnnotatedEndpointFactory annotatedEndpointFactory;
    private final InstanceFactory<?> endpointFactory;
    private final PathTemplate pathTemplate;
    private final EncodingFactory encodingFactory;
    private final Set<Session> openSessions = Collections.newSetFromMap(new ConcurrentHashMap<Session, Boolean>());

    private volatile int waiterCount;

    public ConfiguredServerEndpoint(final ServerEndpointConfig endpointConfiguration, final InstanceFactory<?> endpointFactory, final PathTemplate pathTemplate, final EncodingFactory encodingFactory, AnnotatedEndpointFactory annotatedEndpointFactory) {
        this.endpointConfiguration = endpointConfiguration;
        this.endpointFactory = endpointFactory;
        this.pathTemplate = pathTemplate;
        this.encodingFactory = encodingFactory;
        this.annotatedEndpointFactory = annotatedEndpointFactory;
    }

    public ServerEndpointConfig getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public InstanceFactory<?> getEndpointFactory() {
        return endpointFactory;
    }

    public PathTemplate getPathTemplate() {
        return pathTemplate;
    }

    public EncodingFactory getEncodingFactory() {
        return encodingFactory;
    }

    public Set<Session> getOpenSessions() {
        return openSessions;
    }

    public void addOpenSession(Session session) {
        openSessions.add(session);
    }

    public void removeOpenSession(Session session) {
        synchronized (this) {
            openSessions.remove(session);
            if (waiterCount > 0 && openSessions.isEmpty()) {
                notifyAll();
            }
        }
    }

    public void awaitClose(long timeout) {
        waiterCount++;
        long end = System.currentTimeMillis() + timeout;
        synchronized (this) {
            if(openSessions.isEmpty()) {
                return;
            }
            try {
                while (System.currentTimeMillis() < end) {
                    wait(end - System.currentTimeMillis());
                }
            } catch (InterruptedException e) {
                //ignore
                return;
            } finally {
                waiterCount--;
            }
        }
    }

    public AnnotatedEndpointFactory getAnnotatedEndpointFactory() {
        return annotatedEndpointFactory;
    }
}
