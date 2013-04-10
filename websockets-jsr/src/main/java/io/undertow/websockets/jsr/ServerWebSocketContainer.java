/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.impl.WebSocketChannelSession;
import io.undertow.websockets.jsr.bootstrap.WebSocketDeployment;
import org.xnio.ByteBufferSlicePool;
import org.xnio.IoFuture;
import org.xnio.OptionMap;


/**
 * {@link ServerContainer} implementation which allows to deploy endpoints for a server.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ServerWebSocketContainer implements ServerContainer {

    private final WebSocketDeployment webSocketDeployment;
    private volatile long defaultAsyncSendTimeout;
    private volatile long maxSessionIdleTimeout;
    private volatile int defaultMaxBinaryMessageBufferSize;
    private volatile int defaultMaxTextMessageBufferSize;
    private volatile boolean deploymentComplete = false;

    public ServerWebSocketContainer(final WebSocketDeployment webSocketDeployment) {
        this.webSocketDeployment = webSocketDeployment;
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncSendTimeout;
    }

    @Override
    public void setAsyncSendTimeout(long defaultAsyncSendTimeout) {
        this.defaultAsyncSendTimeout = defaultAsyncSendTimeout;
    }

    @Override
    public Session connectToServer(final Object annotatedEndpointInstance, final URI path) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = webSocketDeployment.getClientEndpoint(annotatedEndpointInstance.getClass());
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(annotatedEndpointInstance.getClass());
        }
        Endpoint instance = config.getFactory().createInstanceForExisting(annotatedEndpointInstance);
        return connectToServer(instance, config.getConfig(), path);
    }

    @Override
    public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = webSocketDeployment.getClientEndpoint(aClass);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(aClass);
        }
        try {
            InstanceHandle<Endpoint> instance = config.getFactory().createInstance();
            return connectToServer(instance.getInstance(), config.getConfig(), uri);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Session connectToServer(final Endpoint endpointInstance, final ClientEndpointConfig cec, final URI path) throws DeploymentException, IOException {
        if (!deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotConnectUntilDeploymentComplete();
        }
        final Map<String, String>


        WebSocketClient client = new WebSocketClient();
        IoFuture<WebSocketChannel> session = WebSocketClient.connect(webSocketDeployment.getHttpClient(), new ByteBufferSlicePool(100, 1000), OptionMap.EMPTY, path, WebSocketVersion.V13); //TODO: fix this
        WebSocketChannel channel = session.get();
        WebSocketChannelSession wss = new WebSocketChannelSession(channel, "", false);
        return new UndertowSession(wss, path, Collections.<String,String>emptyMap(), Collections.emptyMap(), );
        return null;
    }

    @Override
    public Session connectToServer(final Class<? extends Endpoint> endpointClass, final ClientEndpointConfig cec, final URI path) throws DeploymentException, IOException {
        try {
            Endpoint endpoint = endpointClass.newInstance();
            return connectToServer(endpoint, cec, path);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return maxSessionIdleTimeout;
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(final long timeout) {
        this.maxSessionIdleTimeout = timeout;
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return defaultMaxBinaryMessageBufferSize;
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int defaultMaxBinaryMessageBufferSize) {
        this.defaultMaxBinaryMessageBufferSize = defaultMaxBinaryMessageBufferSize;
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return defaultMaxTextMessageBufferSize;
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int defaultMaxTextMessageBufferSize) {
        this.defaultMaxTextMessageBufferSize = defaultMaxTextMessageBufferSize;
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }


    @Override
    public void addEndpoint(final Class<?> endpointClass) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        webSocketDeployment.getDeploymentInfo().addProgramaticAnnotatedEndpoints(endpointClass);
    }

    @Override
    public void addEndpoint(final ServerEndpointConfig serverConfig) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        webSocketDeployment.getDeploymentInfo().addProgramaticEndpoints(serverConfig);
    }
}
