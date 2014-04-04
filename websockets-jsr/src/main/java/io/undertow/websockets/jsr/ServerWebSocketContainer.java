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

import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.util.PathTemplate;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioWorker;

import javax.servlet.DispatcherType;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;


/**
 * {@link ServerContainer} implementation which allows to deploy endpoints for a server.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ServerWebSocketContainer implements ServerContainer {

    private final ClassIntrospecter classIntrospecter;

    private final Map<Class<?>, ConfiguredClientEndpoint> clientEndpoints = new HashMap<Class<?>, ConfiguredClientEndpoint>();

    private final List<ConfiguredServerEndpoint> configuredServerEndpoints = new ArrayList<ConfiguredServerEndpoint>();

    /**
     * set of all deployed server endpoint paths. Due to the comparison function we can detect
     * overlaps
     */
    private final TreeSet<PathTemplate> seenPaths = new TreeSet<PathTemplate>();

    private final XnioWorker xnioWorker;
    private final Pool<ByteBuffer> bufferPool;
    private final ThreadSetupAction threadSetupAction;
    private final boolean dispatchToWorker;

    private final boolean clientMode;

    private volatile long defaultAsyncSendTimeout;
    private volatile long maxSessionIdleTimeout;
    private volatile int defaultMaxBinaryMessageBufferSize;
    private volatile int defaultMaxTextMessageBufferSize;
    private volatile boolean deploymentComplete = false;

    private ServletContextImpl contextToAddFilter = null;


    public ServerWebSocketContainer(final ClassIntrospecter classIntrospecter, XnioWorker xnioWorker, Pool<ByteBuffer> bufferPool, ThreadSetupAction threadSetupAction, boolean dispatchToWorker, boolean clientMode) {
        this.classIntrospecter = classIntrospecter;
        this.bufferPool = bufferPool;
        this.xnioWorker = xnioWorker;
        this.threadSetupAction = threadSetupAction;
        this.dispatchToWorker = dispatchToWorker;
        this.clientMode = clientMode;
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
        IoFuture<WebSocketChannel> session = WebSocketClient.connect(xnioWorker, bufferPool, OptionMap.EMPTY, path, WebSocketVersion.V13); //TODO: fix this
        WebSocketChannel channel = session.get();
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, cec.getDecoders(), cec.getEncoders());
        UndertowSession undertowSession = new UndertowSession(channel, path, Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<Endpoint>(endpointInstance), cec, path.getQuery(), encodingFactory.createEncoding(cec), new HashSet<Session>());
        endpointInstance.onOpen(undertowSession, cec);
        channel.resumeReceives();

        return undertowSession;
    }


    @Override
    public Session connectToServer(final Class<? extends Endpoint> endpointClass, final ClientEndpointConfig cec, final URI path) throws DeploymentException, IOException {
        try {
            Endpoint endpoint = endpointClass.newInstance();
            return connectToServer(endpoint, cec, path);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public Session connectToServerInternal(final Endpoint endpointInstance, final ConfiguredClientEndpoint cec, final URI path) throws DeploymentException, IOException {
        //in theory we should not be able to connect until the deployment is complete, but the definition of when a deployment is complete is a bit nebulous.
        IoFuture<WebSocketChannel> session = WebSocketClient.connect(xnioWorker, bufferPool, OptionMap.EMPTY, path, WebSocketVersion.V13); //TODO: fix this
        WebSocketChannel channel = session.get();
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        UndertowSession undertowSession = new UndertowSession(channel, path, Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<Endpoint>(endpointInstance), cec.getConfig(), path.getQuery(), cec.getEncodingFactory().createEncoding(cec.getConfig()), new HashSet<Session>());
        endpointInstance.onOpen(undertowSession, cec.getConfig());
        channel.resumeReceives();

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

    /**
     * Runs a web socket invocation, setting up the threads and dispatching a thread pool
     * <p/>
     * Unfortunately we need to dispatch to a thread pool, because there is a good chance that the endpoint
     * will use blocking IO methods. We suspend recieves while this is in progress, to make sure that we do not have multiple
     * methods invoked at once.
     * <p/>
     *
     * @param invocation The task to run
     */
    public void invokeEndpointMethod(final Executor executor, final Runnable invocation) {
        if (dispatchToWorker) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    ThreadSetupAction.Handle handle = threadSetupAction.setup(null);
                    try {
                        invocation.run();
                    } finally {
                        handle.tearDown();
                    }
                }
            });
        } else {
            ThreadSetupAction.Handle handle = threadSetupAction.setup(null);
            try {
                invocation.run();
            } finally {
                handle.tearDown();
            }
        }
    }

    @Override
    public void addEndpoint(final Class<?> endpoint) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        addEndpointInternal(endpoint);
    }

    private void addEndpointInternal(final Class<?> endpoint) throws DeploymentException {
        try {
            ServerEndpoint serverEndpoint = endpoint.getAnnotation(ServerEndpoint.class);
            ClientEndpoint clientEndpoint = endpoint.getAnnotation(ClientEndpoint.class);
            if (serverEndpoint != null) {
                JsrWebSocketLogger.ROOT_LOGGER.addingAnnotatedServerEndpoint(endpoint, serverEndpoint.value());
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
                AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(xnioWorker, endpoint, classIntrospecter.createInstanceFactory(endpoint), encodingFactory, template.getParameterNames());
                Class<? extends ServerEndpointConfig.Configurator> configuratorClass = serverEndpoint.configurator();
                ServerEndpointConfig.Configurator configurator;
                if (configuratorClass != ServerEndpointConfig.Configurator.class) {
                    configurator = configuratorClass.newInstance();
                } else {
                    configurator = new ServerInstanceFactoryConfigurator(factory);
                }

                ServerEndpointConfig config = ServerEndpointConfig.Builder.create(endpoint, serverEndpoint.value())
                        .decoders(Arrays.asList(serverEndpoint.decoders()))
                        .encoders(Arrays.asList(serverEndpoint.encoders()))
                        .subprotocols(Arrays.asList(serverEndpoint.subprotocols()))
                        .configurator(configurator)
                        .build();


                ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(config, factory, template, encodingFactory);
                configuredServerEndpoints.add(confguredServerEndpoint);
                handleAddingFilterMapping();
            } else if (clientEndpoint != null) {
                JsrWebSocketLogger.ROOT_LOGGER.addingAnnotatedClientEndpoint(endpoint);
                EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, clientEndpoint.decoders(), clientEndpoint.encoders());
                AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(xnioWorker, endpoint, classIntrospecter.createInstanceFactory(endpoint), encodingFactory, Collections.<String>emptySet());

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

        } catch (NoSuchMethodException e) {
            throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
        } catch (InstantiationException e) {
            throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
        } catch (IllegalAccessException e) {
            throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
        }
    }


    private void handleAddingFilterMapping() {
        if (contextToAddFilter != null) {
            contextToAddFilter.getDeployment().getDeploymentInfo().addFilterUrlMapping(Bootstrap.FILTER_NAME, "/*", DispatcherType.REQUEST);
            contextToAddFilter.getDeployment().getServletPaths().invalidate();
            contextToAddFilter = null;
        }
    }

    @Override
    public void addEndpoint(final ServerEndpointConfig endpoint) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        JsrWebSocketLogger.ROOT_LOGGER.addingProgramaticEndpoint(endpoint.getEndpointClass(), endpoint.getPath());
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
        handleAddingFilterMapping();
    }


    public ConfiguredClientEndpoint getClientEndpoint(final Class<?> type) {
        ConfiguredClientEndpoint existing = clientEndpoints.get(type);
        if (existing != null) {
            return existing;
        }
        if (clientMode && type.isAnnotationPresent(ClientEndpoint.class)) {
            try {
                addEndpointInternal(type);
                return clientEndpoints.get(type);
            } catch (DeploymentException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    public void deploymentComplete() {
        deploymentComplete = true;
    }

    public List<ConfiguredServerEndpoint> getConfiguredServerEndpoints() {
        return configuredServerEndpoints;
    }

    public ServletContextImpl getContextToAddFilter() {
        return contextToAddFilter;
    }

    public void setContextToAddFilter(ServletContextImpl contextToAddFilter) {
        this.contextToAddFilter = contextToAddFilter;
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
}
