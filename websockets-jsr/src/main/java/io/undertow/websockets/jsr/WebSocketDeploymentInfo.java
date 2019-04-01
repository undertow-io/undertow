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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import javax.websocket.server.ServerEndpointConfig;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;

/**
 * Web socket deployment information
 *
 * @author Stuart Douglas
 */
public class WebSocketDeploymentInfo implements Cloneable {

    public static final String ATTRIBUTE_NAME = "io.undertow.websockets.jsr.WebSocketDeploymentInfo";

    private boolean dispatchToWorkerThread = false;
    private final List<Class<?>> annotatedEndpoints = new ArrayList<>();
    private final List<ServerEndpointConfig> programaticEndpoints = new ArrayList<>();
    private final List<ContainerReadyListener> containerReadyListeners = new ArrayList<>();
    private final List<WebSocketServerExtensionHandshaker> serverExtensions = new ArrayList<>();
    private String clientBindAddress = null;
    private WebSocketReconnectHandler reconnectHandler;
    private EventLoopGroup eventLoopGroup;
    private Executor executor;

    public WebSocketDeploymentInfo addEndpoint(final Class<?> annotated) {
        this.annotatedEndpoints.add(annotated);
        return this;
    }

    public WebSocketDeploymentInfo addAnnotatedEndpoints(final Collection<Class<?>> annotatedEndpoints) {
        this.annotatedEndpoints.addAll(annotatedEndpoints);
        return this;
    }

    public WebSocketDeploymentInfo addEndpoint(final ServerEndpointConfig endpoint) {
        this.programaticEndpoints.add(endpoint);
        return this;
    }

    public WebSocketDeploymentInfo addProgramaticEndpoints(final Collection<ServerEndpointConfig> programaticEndpoints) {
        this.programaticEndpoints.addAll(programaticEndpoints);
        return this;
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    public Executor getExecutor() {
        return executor;
    }

    public WebSocketDeploymentInfo setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    public WebSocketDeploymentInfo setEventLoopGroup(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public List<Class<?>> getAnnotatedEndpoints() {
        return annotatedEndpoints;
    }

    public List<ServerEndpointConfig> getProgramaticEndpoints() {
        return programaticEndpoints;
    }

    void containerReady(ServerWebSocketContainer container) {
        for (ContainerReadyListener listener : containerReadyListeners) {
            listener.ready(container);
        }
    }

    public WebSocketDeploymentInfo addListener(final ContainerReadyListener listener) {
        containerReadyListeners.add(listener);
        return this;
    }

    public WebSocketDeploymentInfo addListeners(final Collection<ContainerReadyListener> listeners) {
        containerReadyListeners.addAll(listeners);
        return this;
    }

    public List<ContainerReadyListener> getListeners() {
        return containerReadyListeners;
    }

    public boolean isDispatchToWorkerThread() {
        return dispatchToWorkerThread;
    }

    public WebSocketDeploymentInfo setDispatchToWorkerThread(boolean dispatchToWorkerThread) {
        this.dispatchToWorkerThread = dispatchToWorkerThread;
        return this;
    }

    public interface ContainerReadyListener {
        void ready(ServerWebSocketContainer container);
    }

    /**
     * Add a new WebSocket Extension into this deployment info.
     *
     * @param extension a new {@code ExtensionHandshake} instance
     * @return current deployment info
     */
    public WebSocketDeploymentInfo addServerExtension(final WebSocketServerExtensionHandshaker extension) {
        if (null != extension) {
            this.serverExtensions.add(extension);
        }
        return this;
    }

    public WebSocketDeploymentInfo addServerExtensions(final Collection<WebSocketServerExtensionHandshaker> extensions) {
        this.serverExtensions.addAll(extensions);
        return this;
    }

    /**
     * @return list of extensions available for this deployment info
     */
    public List<WebSocketServerExtensionHandshaker> getServerExtensions() {
        return serverExtensions;
    }

    public String getClientBindAddress() {
        return clientBindAddress;
    }

    public WebSocketDeploymentInfo setClientBindAddress(String clientBindAddress) {
        this.clientBindAddress = clientBindAddress;
        return this;
    }

    public WebSocketReconnectHandler getReconnectHandler() {
        return reconnectHandler;
    }

    public WebSocketDeploymentInfo setReconnectHandler(WebSocketReconnectHandler reconnectHandler) {
        this.reconnectHandler = reconnectHandler;
        return this;
    }

    @Override
    public WebSocketDeploymentInfo clone() {
        return new WebSocketDeploymentInfo()
                .setDispatchToWorkerThread(this.dispatchToWorkerThread)
                .addAnnotatedEndpoints(this.annotatedEndpoints)
                .addProgramaticEndpoints(this.programaticEndpoints)
                .addListeners(this.containerReadyListeners)
                .addServerExtensions(this.serverExtensions)
                .setClientBindAddress(this.clientBindAddress)
                .setReconnectHandler(this.reconnectHandler)
                ;
    }

}
