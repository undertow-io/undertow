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

import io.undertow.server.XnioByteBufferPool;
import io.undertow.websockets.extensions.ExtensionHandshake;
import io.undertow.connector.ByteBufferPool;
import org.xnio.Pool;
import org.xnio.XnioWorker;

import jakarta.websocket.server.ServerEndpointConfig;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Web socket deployment information
 *
 * @author Stuart Douglas
 */
public class WebSocketDeploymentInfo implements Cloneable {

    public static final String ATTRIBUTE_NAME = "io.undertow.websockets.jsr.WebSocketDeploymentInfo";

    private Supplier<XnioWorker> worker = new Supplier<XnioWorker>() {

        volatile XnioWorker worker;

        @Override
        public XnioWorker get() {
            if(worker != null) {
                return worker;
            }
            return worker = UndertowContainerProvider.getDefaultContainer().getXnioWorker();
        }
    };
    private ByteBufferPool buffers;
    private boolean dispatchToWorkerThread = false;
    private final List<Class<?>> annotatedEndpoints = new ArrayList<>();
    private final List<ServerEndpointConfig> programaticEndpoints = new ArrayList<>();
    private final List<ContainerReadyListener> containerReadyListeners = new ArrayList<>();
    private final List<ExtensionHandshake> extensions = new ArrayList<>();
    private String clientBindAddress = null;
    private WebSocketReconnectHandler reconnectHandler;

    public Supplier<XnioWorker> getWorker() {
        return worker;
    }

    public WebSocketDeploymentInfo setWorker(Supplier<XnioWorker> worker) {
        this.worker = worker;
        return this;
    }

    public WebSocketDeploymentInfo setWorker(XnioWorker worker) {
        this.worker = new Supplier<XnioWorker>() {
            @Override
            public XnioWorker get() {
                return worker;
            }
        };
        return this;
    }

    public ByteBufferPool getBuffers() {
        return buffers;
    }

    @Deprecated
    public WebSocketDeploymentInfo setBuffers(Pool<ByteBuffer> buffers) {
        return setBuffers(new XnioByteBufferPool(buffers));
    }

    public WebSocketDeploymentInfo setBuffers(ByteBufferPool buffers) {
        this.buffers = buffers;
        return this;
    }

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
     * @return          current deployment info
     */
    public WebSocketDeploymentInfo addExtension(final ExtensionHandshake extension) {
        if (null != extension) {
            this.extensions.add(extension);
        }
        return this;
    }

    public WebSocketDeploymentInfo addExtensions(final Collection<ExtensionHandshake> extensions) {
        this.extensions.addAll(extensions);
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
                .setWorker(this.worker)
                .setBuffers(this.buffers)
                .setDispatchToWorkerThread(this.dispatchToWorkerThread)
                .addAnnotatedEndpoints(this.annotatedEndpoints)
                .addProgramaticEndpoints(this.programaticEndpoints)
                .addListeners(this.containerReadyListeners)
                .addExtensions(this.extensions)
                .setClientBindAddress(this.clientBindAddress)
                .setReconnectHandler(this.reconnectHandler)
        ;
    }

}
