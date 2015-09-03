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

import io.undertow.websockets.extensions.ExtensionHandshake;
import io.undertow.connector.ByteBufferPool;
import org.xnio.XnioWorker;

import javax.websocket.server.ServerEndpointConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Web socket deployment information
 *
 * @author Stuart Douglas
 */
public class WebSocketDeploymentInfo {

    public static final String ATTRIBUTE_NAME = "io.undertow.websockets.jsr.WebSocketDeploymentInfo";

    private XnioWorker worker;
    private ByteBufferPool buffers;
    private boolean dispatchToWorkerThread = false;
    private final List<Class<?>> annotatedEndpoints = new ArrayList<>();
    private final List<ServerEndpointConfig> programaticEndpoints = new ArrayList<>();
    private final List<ContainerReadyListener> containerReadyListeners = new ArrayList<>();
    private final List<ExtensionHandshake> extensions = new ArrayList<>();
    private String clientBindAddress = null;
    private WebSocketReconnectHandler reconnectHandler;

    public XnioWorker getWorker() {
        return worker;
    }

    public WebSocketDeploymentInfo setWorker(XnioWorker worker) {
        this.worker = worker;
        return this;
    }

    public ByteBufferPool getBuffers() {
        return buffers;
    }

    public WebSocketDeploymentInfo setBuffers(ByteBufferPool buffers) {
        this.buffers = buffers;
        return this;
    }

    public WebSocketDeploymentInfo addEndpoint(final Class<?> annotated) {
        this.annotatedEndpoints.add(annotated);
        return this;
    }

    public WebSocketDeploymentInfo addEndpoint(final ServerEndpointConfig endpoint) {
        this.programaticEndpoints.add(endpoint);
        return this;
    }

    public List<Class<?>> getAnnotatedEndpoints() {
        return annotatedEndpoints;
    }

    public List<ServerEndpointConfig> getProgramaticEndpoints() {
        return programaticEndpoints;
    }

    void containerReady(ServerWebSocketContainer container) {
        for(ContainerReadyListener listener : containerReadyListeners) {
            listener.ready(container);
        }
    }

    public WebSocketDeploymentInfo addListener(final ContainerReadyListener listener) {
        containerReadyListeners.add(listener);
        return this;
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
     * @return          current deployment info
     */
    public WebSocketDeploymentInfo addExtension(final ExtensionHandshake extension) {
        if (null != extension) {
            this.extensions.add(extension);
        }
        return this;
    }

    /**
     * @return list of extensions available for this deployment info
     */
    public List<ExtensionHandshake> getExtensions() {
        return extensions;
    }

    public String getClientBindAddress() {
        return clientBindAddress;
    }

    public void setClientBindAddress(String clientBindAddress) {
        this.clientBindAddress = clientBindAddress;
    }

    public WebSocketReconnectHandler getReconnectHandler() {
        return reconnectHandler;
    }

    public WebSocketDeploymentInfo setReconnectHandler(WebSocketReconnectHandler reconnectHandler) {
        this.reconnectHandler = reconnectHandler;
        return this;
    }
}
