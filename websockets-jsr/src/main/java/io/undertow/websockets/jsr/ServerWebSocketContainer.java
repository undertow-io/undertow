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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.client.HttpClient;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.websockets.api.WebSocketSessionIdGenerator;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import io.undertow.websockets.impl.WebSocketChannelSession;
import io.undertow.websockets.impl.WebSocketRecieveListeners;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;


/**
 * {@link ServerContainer} implementation which allows to deploy endpoints for a server.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ServerWebSocketContainer implements ServerContainer {

    private final ClassIntrospecter classIntrospecter;

    private final WebSocketSessionIdGenerator sessionIdGenerator = new UuidWebSocketSessionIdGenerator();

    private final Map<Class<?>, ConfiguredClientEndpoint> clientEndpoints = new HashMap<>();

    private final List<ConfiguredServerEndpoint> configuredServerEndpoints = new ArrayList<>();

    /**
     * set of all deployed server endpoint paths. Due to the comparison function we can detect
     * overlaps
     */
    private final TreeSet<PathTemplate> seenPaths = new TreeSet<>();

    private HttpClient httpClient;
    private Pool<ByteBuffer> bufferPool;

    private volatile long defaultAsyncSendTimeout;
    private volatile long maxSessionIdleTimeout;
    private volatile int defaultMaxBinaryMessageBufferSize;
    private volatile int defaultMaxTextMessageBufferSize;
    private volatile boolean deploymentComplete = false;

    public ServerWebSocketContainer(final ClassIntrospecter classIntrospecter) {
        this.classIntrospecter = classIntrospecter;
    }


    public void start(HttpClient httpClient, Pool<ByteBuffer> bufferPool) {
        this.httpClient = httpClient;
        this.bufferPool = bufferPool;
    }

    public void stop() {
        this.httpClient = null;
        this.bufferPool = null;
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
        ConfiguredClientEndpoint config = getClientEndpoint(annotatedEndpointInstance.getClass());
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(annotatedEndpointInstance.getClass());
        }
        Endpoint instance = config.getFactory().createInstanceForExisting(annotatedEndpointInstance);
        return connectToServerInternal(instance, config, path);
    }

    @Override
    public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = getClientEndpoint(aClass);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(aClass);
        }
        try {
            InstanceHandle<Endpoint> instance = config.getFactory().createInstance();
            return connectToServerInternal(instance.getInstance(), config, uri);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Session connectToServer(final Endpoint endpointInstance, final ClientEndpointConfig cec, final URI path) throws DeploymentException, IOException {
        //in theory we should not be able to connect until the deployment is complete, but the definition of when a deployment is complete is a bit nebulous.
        IoFuture<WebSocketChannel> session = WebSocketClient.connect(httpClient, bufferPool, OptionMap.EMPTY, path, WebSocketVersion.V13); //TODO: fix this
        WebSocketChannel channel = session.get();
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        WebSocketChannelSession wss = new WebSocketChannelSession(channel, sessionIdGenerator.nextId(), false);

        WebSocketRecieveListeners.startRecieving(wss, channel, false);
        EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, cec.getDecoders(), cec.getEncoders());
        UndertowSession undertowSession = new UndertowSession(wss, path, Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<>(endpointInstance), cec, encodingFactory.createEncoding(cec));
        endpointInstance.onOpen(undertowSession, cec);

        return undertowSession;
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

    public Session connectToServerInternal(final Endpoint endpointInstance, final ConfiguredClientEndpoint cec, final URI path) throws DeploymentException, IOException {
        //in theory we should not be able to connect until the deployment is complete, but the definition of when a deployment is complete is a bit nebulous.
        IoFuture<WebSocketChannel> session = WebSocketClient.connect(httpClient, bufferPool, OptionMap.EMPTY, path, WebSocketVersion.V13); //TODO: fix this
        WebSocketChannel channel = session.get();
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        WebSocketChannelSession wss = new WebSocketChannelSession(channel, sessionIdGenerator.nextId(), false);

        WebSocketRecieveListeners.startRecieving(wss, channel, false);

        UndertowSession undertowSession = new UndertowSession(wss, path, Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<>(endpointInstance), cec.getConfig(), cec.getEncodingFactory().createEncoding(cec.getConfig()));
        endpointInstance.onOpen(undertowSession, cec.getConfig());

        return undertowSession;
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
    public void addEndpoint(final Class<?> endpoint) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        try {
            ServerEndpoint serverEndpoint = endpoint.getAnnotation(ServerEndpoint.class);
            ClientEndpoint clientEndpoint = endpoint.getAnnotation(ClientEndpoint.class);
            if (serverEndpoint != null) {
                final PathTemplate template = PathTemplate.create(serverEndpoint.value());
                if (seenPaths.contains(template)) {
                    PathTemplate existing = null;
                    for (PathTemplate p : seenPaths) {
                        if (p.compareTo(template) == 0) {
                            existing = p;
                            break;
                        }
                    }
                    throw JsrWebSocketMessages.MESSAGES.multipleEndpointsWithOverlappingPaths(template, existing);
                }
                seenPaths.add(template);

                EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, serverEndpoint.decoders(), serverEndpoint.encoders());
                AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(endpoint, classIntrospecter.createInstanceFactory(endpoint), encodingFactory);

                ServerEndpointConfig config = ServerEndpointConfig.Builder.create(endpoint, serverEndpoint.value())
                        .decoders(Arrays.asList(serverEndpoint.decoders()))
                        .encoders(Arrays.asList(serverEndpoint.encoders()))
                        .subprotocols(Arrays.asList(serverEndpoint.subprotocols()))
                        .configurator(new ServerInstanceFactoryConfigurator(factory))
                        .build();


                ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(config, factory, template, encodingFactory);
                configuredServerEndpoints.add(confguredServerEndpoint);
            } else if (clientEndpoint != null) {
                EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, clientEndpoint.decoders(), clientEndpoint.encoders());
                AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(endpoint, classIntrospecter.createInstanceFactory(endpoint), encodingFactory);

                ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                        .decoders(Arrays.asList(clientEndpoint.decoders()))
                        .encoders(Arrays.asList(clientEndpoint.encoders()))
                        .preferredSubprotocols(Arrays.asList(clientEndpoint.subprotocols()))
                        .configurator(clientEndpoint.configurator().newInstance())
                        .build();

                ConfiguredClientEndpoint configuredClientEndpoint = new ConfiguredClientEndpoint(config, factory, encodingFactory);
                clientEndpoints.put(endpoint, configuredClientEndpoint);
            } else {
                throw JsrWebSocketMessages.MESSAGES.classWasNotAnnotated(endpoint);
            }

        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
        }
    }

    @Override
    public void addEndpoint(final ServerEndpointConfig endpoint) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        final PathTemplate template = PathTemplate.create(endpoint.getPath());
        if (seenPaths.contains(template)) {
            PathTemplate existing = null;
            for (PathTemplate p : seenPaths) {
                if (p.compareTo(template) == 0) {
                    existing = p;
                    break;
                }
            }
            throw JsrWebSocketMessages.MESSAGES.multipleEndpointsWithOverlappingPaths(template, existing);
        }
        seenPaths.add(template);
        EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, endpoint.getDecoders(), endpoint.getEncoders());
        ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(endpoint, null, template, encodingFactory);
        configuredServerEndpoints.add(confguredServerEndpoint);
    }


    public ConfiguredClientEndpoint getClientEndpoint(final Class<?> type) {
        return clientEndpoints.get(type);
    }


    public void deploymentComplete() {
        deploymentComplete = true;
    }

    private static final class ServerInstanceFactoryConfigurator extends ServerEndpointConfig.Configurator {

        private final InstanceFactory<?> factory;

        private ServerInstanceFactoryConfigurator(final InstanceFactory<?> factory) {
            this.factory = factory;
        }

        @Override
        public <T> T getEndpointInstance(final Class<T> endpointClass) throws InstantiationException {
            return (T) factory.createInstance().getInstance();
        }
    }

    public List<ConfiguredServerEndpoint> getConfiguredServerEndpoints() {
        return configuredServerEndpoints;
    }
}
