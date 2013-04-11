/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.websockets.jsr.bootstrap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.websockets.api.WebSocketSessionIdGenerator;
import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import org.xnio.Pool;

/**
 * The deployment info that is used to build up a web socket deployment.
 *
 * @author Stuart Douglas
 */
public class WebSocketDeploymentInfo {

    private final Set<Class<?>> annotatedEndpoints = new HashSet<>();
    private Pool<ByteBuffer> bufferPool;
    private WebSocketSessionIdGenerator sessionIdGenerator = new UuidWebSocketSessionIdGenerator();
    private final Set<Class<? extends Endpoint>> discoveredEndpoints = new HashSet<>();
    private final Set<Class<? extends ServerApplicationConfig>> serverApplicationConfigClasses = new HashSet<>();
    private final Set<ServerEndpointConfig> programaticEndpoints = new HashSet<>();
    private final Set<Class<?>> programaticAnnotatedEndpoints = new HashSet<>();

    public WebSocketDeploymentInfo addAnnotatedEndpoints(final Class<?>... endpoints) {
        annotatedEndpoints.addAll(Arrays.asList(endpoints));
        return this;
    }

    public Set<Class<?>> getAnnotatedEndpoints() {
        return Collections.unmodifiableSet(annotatedEndpoints);
    }

    public WebSocketDeploymentInfo addDiscoveredEndpoints(final Class<? extends Endpoint>... endpoints) {
        discoveredEndpoints.addAll(Arrays.asList(endpoints));
        return this;
    }

    public Set<Class<? extends Endpoint>> getDiscoveredEndpoints() {
        return Collections.unmodifiableSet(discoveredEndpoints);
    }

    public WebSocketDeploymentInfo addServerApplicationConfigClasses(final Class<? extends ServerApplicationConfig>... classes) {
        serverApplicationConfigClasses.addAll(Arrays.asList(classes));
        return this;
    }

    public Set<Class<? extends ServerApplicationConfig>> getServerApplicationConfigClass() {
        return Collections.unmodifiableSet(serverApplicationConfigClasses);
    }

    public WebSocketDeploymentInfo addProgramaticEndpoints(final ServerEndpointConfig... endpoints) {
        programaticEndpoints.addAll(Arrays.asList(endpoints));
        return this;
    }

    public Set<ServerEndpointConfig> getProgramaticEndpoints() {
        return Collections.unmodifiableSet(programaticEndpoints);
    }

    public WebSocketDeploymentInfo addProgramaticAnnotatedEndpoints(final Class<?>... endpoints) {
        programaticAnnotatedEndpoints.addAll(Arrays.asList(endpoints));
        return this;
    }

    public Set<Class<?>> getProgramaticAnnotatedEndpoints() {
        return Collections.unmodifiableSet(programaticAnnotatedEndpoints);
    }

    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    public void setBufferPool(final Pool<ByteBuffer> bufferPool) {
        this.bufferPool = bufferPool;
    }

    public WebSocketSessionIdGenerator getSessionIdGenerator() {
        return sessionIdGenerator;
    }

    public void setSessionIdGenerator(final WebSocketSessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * @return true if there are no web socket endpoints, and no application config classes.
     */
    public boolean isEmpty() {
        return annotatedEndpoints.isEmpty() &&
                discoveredEndpoints.isEmpty() &&
                serverApplicationConfigClasses.isEmpty() &&
                programaticEndpoints.isEmpty() &&
                programaticAnnotatedEndpoints.isEmpty();
    }
}
