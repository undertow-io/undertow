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

import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.util.ConstructorInstanceFactory;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.util.PathTemplate;
import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.client.WebSocketClientNegotiation;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.XnioWorker;
import org.xnio.http.UpgradeFailedException;
import org.xnio.ssl.XnioSsl;

import javax.servlet.DispatcherType;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


/**
 * {@link ServerContainer} implementation which allows to deploy endpoints for a server.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ServerWebSocketContainer implements ServerContainer, Closeable {

    public static final String TIMEOUT = "io.undertow.websocket.CONNECT_TIMEOUT";
    public static final int DEFAULT_WEB_SOCKET_TIMEOUT_SECONDS = 10;

    private final ClassIntrospecter classIntrospecter;

    private final Map<Class<?>, ConfiguredClientEndpoint> clientEndpoints = new HashMap<>();

    private final List<ConfiguredServerEndpoint> configuredServerEndpoints = new ArrayList<>();

    /**
     * set of all deployed server endpoint paths. Due to the comparison function we can detect
     * overlaps
     */
    private final TreeSet<PathTemplate> seenPaths = new TreeSet<>();

    private final XnioWorker xnioWorker;
    private final Pool<ByteBuffer> bufferPool;
    private final ThreadSetupAction threadSetupAction;
    private final boolean dispatchToWorker;
    private final InetSocketAddress clientBindAddress;
    private final WebSocketReconnectHandler webSocketReconnectHandler;

    private volatile long defaultAsyncSendTimeout;
    private volatile long defaultMaxSessionIdleTimeout;
    private volatile int defaultMaxBinaryMessageBufferSize;
    private volatile int defaultMaxTextMessageBufferSize;
    private volatile boolean deploymentComplete = false;

    private ServletContextImpl contextToAddFilter = null;

    private final List<WebsocketClientSslProvider> clientSslProviders;

    public ServerWebSocketContainer(final ClassIntrospecter classIntrospecter, final XnioWorker xnioWorker, Pool<ByteBuffer> bufferPool, ThreadSetupAction threadSetupAction, boolean dispatchToWorker, boolean clientMode) {
        this(classIntrospecter, ServerWebSocketContainer.class.getClassLoader(), xnioWorker, bufferPool, threadSetupAction, dispatchToWorker, null, null);
    }

    public ServerWebSocketContainer(final ClassIntrospecter classIntrospecter, final ClassLoader classLoader, XnioWorker xnioWorker, Pool<ByteBuffer> bufferPool, ThreadSetupAction threadSetupAction, boolean dispatchToWorker) {
        this(classIntrospecter, classLoader, xnioWorker, bufferPool, threadSetupAction, dispatchToWorker, null, null);
    }

    public ServerWebSocketContainer(final ClassIntrospecter classIntrospecter, final ClassLoader classLoader, XnioWorker xnioWorker, Pool<ByteBuffer> bufferPool, ThreadSetupAction threadSetupAction, boolean dispatchToWorker, InetSocketAddress clientBindAddress, WebSocketReconnectHandler reconnectHandler) {
        this.classIntrospecter = classIntrospecter;
        this.bufferPool = bufferPool;
        this.xnioWorker = xnioWorker;
        this.threadSetupAction = threadSetupAction;
        this.dispatchToWorker = dispatchToWorker;
        this.clientBindAddress = clientBindAddress;
        List<WebsocketClientSslProvider> clientSslProviders = new ArrayList<>();
        for (WebsocketClientSslProvider provider : ServiceLoader.load(WebsocketClientSslProvider.class, classLoader)) {
            clientSslProviders.add(provider);
        }

        this.clientSslProviders = Collections.unmodifiableList(clientSslProviders);
        this.webSocketReconnectHandler = reconnectHandler;
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncSendTimeout;
    }

    @Override
    public void setAsyncSendTimeout(long defaultAsyncSendTimeout) {
        this.defaultAsyncSendTimeout = defaultAsyncSendTimeout;
    }

    public Session connectToServer(final Object annotatedEndpointInstance, WebSocketClient.ConnectionBuilder connectionBuilder) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = getClientEndpoint(annotatedEndpointInstance.getClass(), false);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(annotatedEndpointInstance.getClass());
        }
        Endpoint instance = config.getFactory().createInstance(new ImmediateInstanceHandle<>(annotatedEndpointInstance));
        return connectToServerInternal(instance, config, connectionBuilder);
    }

    @Override
    public Session connectToServer(final Object annotatedEndpointInstance, final URI path) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = getClientEndpoint(annotatedEndpointInstance.getClass(), false);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(annotatedEndpointInstance.getClass());
        }
        Endpoint instance = config.getFactory().createInstance(new ImmediateInstanceHandle<>(annotatedEndpointInstance));
        XnioSsl ssl = null;
        for (WebsocketClientSslProvider provider : clientSslProviders) {
            ssl = provider.getSsl(xnioWorker, annotatedEndpointInstance, path);
            if (ssl != null) {
                break;
            }
        }
        return connectToServerInternal(instance, ssl, config, path);
    }

    public Session connectToServer(Class<?> aClass, WebSocketClient.ConnectionBuilder connectionBuilder) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = getClientEndpoint(aClass, true);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(aClass);
        }
        try {
            AnnotatedEndpointFactory factory = config.getFactory();
            InstanceHandle<?> instance = config.getInstanceFactory().createInstance();
            return connectToServerInternal(factory.createInstance(instance), config, connectionBuilder);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException, IOException {
        ConfiguredClientEndpoint config = getClientEndpoint(aClass, true);
        if (config == null) {
            throw JsrWebSocketMessages.MESSAGES.notAValidClientEndpointType(aClass);
        }
        try {
            AnnotatedEndpointFactory factory = config.getFactory();


            InstanceHandle<?> instance = config.getInstanceFactory().createInstance();
            XnioSsl ssl = null;
            for (WebsocketClientSslProvider provider : clientSslProviders) {
                ssl = provider.getSsl(xnioWorker, aClass, uri);
                if (ssl != null) {
                    break;
                }
            }
            return connectToServerInternal(factory.createInstance(instance), ssl, config, uri);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Session connectToServer(final Endpoint endpointInstance, final ClientEndpointConfig config, final URI path) throws DeploymentException, IOException {
        ClientEndpointConfig cec = config != null ? config : ClientEndpointConfig.Builder.create().build();
        XnioSsl ssl = null;
        for (WebsocketClientSslProvider provider : clientSslProviders) {
            ssl = provider.getSsl(xnioWorker, endpointInstance, cec, path);
            if (ssl != null) {
                break;
            }
        }
        //in theory we should not be able to connect until the deployment is complete, but the definition of when a deployment is complete is a bit nebulous.
        WebSocketClientNegotiation clientNegotiation = new ClientNegotiation(cec.getPreferredSubprotocols(), toExtensionList(cec.getExtensions()), cec);


        WebSocketClient.ConnectionBuilder connectionBuilder = WebSocketClient.connectionBuilder(xnioWorker, bufferPool, path)
                .setSsl(ssl)
                .setBindAddress(clientBindAddress)
                .setClientNegotiation(clientNegotiation);

        return connectToServer(endpointInstance, config, connectionBuilder);
    }

    public Session connectToServer(final Endpoint endpointInstance, final ClientEndpointConfig config, WebSocketClient.ConnectionBuilder connectionBuilder) throws DeploymentException, IOException {
        ClientEndpointConfig cec = config != null ? config : ClientEndpointConfig.Builder.create().build();

        WebSocketClientNegotiation clientNegotiation = connectionBuilder.getClientNegotiation();

        IoFuture<WebSocketChannel> session = connectionBuilder
                .connect();
        Number timeout = (Number) cec.getUserProperties().get(TIMEOUT);
        if(session.await(timeout == null ? DEFAULT_WEB_SOCKET_TIMEOUT_SECONDS: timeout.intValue(), TimeUnit.SECONDS) == IoFuture.Status.WAITING) {
            //add a notifier to close the channel if the connection actually completes
            session.cancel();
            session.addNotifier(new IoFuture.HandlingNotifier<WebSocketChannel, Object>() {
                @Override
                public void handleDone(WebSocketChannel data, Object attachment) {
                    IoUtils.safeClose(data);
                }
            }, null);
            throw JsrWebSocketMessages.MESSAGES.connectionTimedOut();
        }
        WebSocketChannel channel;
        try {
            channel = session.get();
        } catch (UpgradeFailedException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        final List<Extension> extensions = new ArrayList<>();
        final Map<String, Extension> extMap = new HashMap<>();
        for (Extension ext : cec.getExtensions()) {
            extMap.put(ext.getName(), ext);
        }
        for (WebSocketExtension e : clientNegotiation.getSelectedExtensions()) {
            Extension ext = extMap.get(e.getName());
            if (ext == null) {
                throw JsrWebSocketMessages.MESSAGES.extensionWasNotPresentInClientHandshake(e.getName(), clientNegotiation.getSupportedExtensions());
            }
            extensions.add(ExtensionImpl.create(e));
        }

        EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, cec.getDecoders(), cec.getEncoders());
        UndertowSession undertowSession = new UndertowSession(channel, connectionBuilder.getUri(), Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<>(endpointInstance), cec, connectionBuilder.getUri().getQuery(), encodingFactory.createEncoding(cec), new HashSet<Session>(), clientNegotiation.getSelectedSubProtocol(), extensions, connectionBuilder);
        endpointInstance.onOpen(undertowSession, cec);
        channel.resumeReceives();

        return undertowSession;
    }


    @Override
    public Session connectToServer(final Class<? extends Endpoint> endpointClass, final ClientEndpointConfig cec, final URI path) throws DeploymentException, IOException {
        try {
            Endpoint endpoint = classIntrospecter.createInstanceFactory(endpointClass).createInstance().getInstance();
            return connectToServer(endpoint, cec, path);
        } catch (InstantiationException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Session connectToServerInternal(final Endpoint endpointInstance, XnioSsl ssl, final ConfiguredClientEndpoint cec, final URI path) throws DeploymentException, IOException {
        //in theory we should not be able to connect until the deployment is complete, but the definition of when a deployment is complete is a bit nebulous.
        WebSocketClientNegotiation clientNegotiation = new ClientNegotiation(cec.getConfig().getPreferredSubprotocols(), toExtensionList(cec.getConfig().getExtensions()), cec.getConfig());


        WebSocketClient.ConnectionBuilder connectionBuilder = WebSocketClient.connectionBuilder(xnioWorker, bufferPool, path)
                .setSsl(ssl)
                .setBindAddress(clientBindAddress)
                .setClientNegotiation(clientNegotiation);
        return connectToServerInternal(endpointInstance, cec, connectionBuilder);
    }

    private Session connectToServerInternal(final Endpoint endpointInstance, final ConfiguredClientEndpoint cec, WebSocketClient.ConnectionBuilder connectionBuilder) throws DeploymentException, IOException {

        IoFuture<WebSocketChannel> session = connectionBuilder
                .connect();
        Number timeout = (Number) cec.getConfig().getUserProperties().get(TIMEOUT);
        IoFuture.Status result = session.await(timeout == null ? DEFAULT_WEB_SOCKET_TIMEOUT_SECONDS : timeout.intValue(), TimeUnit.SECONDS);
        if(result == IoFuture.Status.WAITING) {
            //add a notifier to close the channel if the connection actually completes

            session.cancel();
            session.addNotifier(new IoFuture.HandlingNotifier<WebSocketChannel, Object>() {
                @Override
                public void handleDone(WebSocketChannel data, Object attachment) {
                    IoUtils.safeClose(data);
                }
            }, null);
            throw JsrWebSocketMessages.MESSAGES.connectionTimedOut();
        }

        WebSocketChannel channel;
        try {
            channel = session.get();
        } catch (UpgradeFailedException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
        EndpointSessionHandler sessionHandler = new EndpointSessionHandler(this);

        final List<Extension> extensions = new ArrayList<>();
        final Map<String, Extension> extMap = new HashMap<>();
        for (Extension ext : cec.getConfig().getExtensions()) {
            extMap.put(ext.getName(), ext);
        }
        String subProtocol = null;
        if(connectionBuilder.getClientNegotiation() != null) {
            for (WebSocketExtension e : connectionBuilder.getClientNegotiation().getSelectedExtensions()) {
                Extension ext = extMap.get(e.getName());
                if (ext == null) {
                    throw JsrWebSocketMessages.MESSAGES.extensionWasNotPresentInClientHandshake(e.getName(), connectionBuilder.getClientNegotiation().getSupportedExtensions());
                }
                extensions.add(ExtensionImpl.create(e));
            }
            subProtocol = connectionBuilder.getClientNegotiation().getSelectedSubProtocol();
        }

        UndertowSession undertowSession = new UndertowSession(channel, connectionBuilder.getUri(), Collections.<String, String>emptyMap(), Collections.<String, List<String>>emptyMap(), sessionHandler, null, new ImmediateInstanceHandle<>(endpointInstance), cec.getConfig(), connectionBuilder.getUri().getQuery(), cec.getEncodingFactory().createEncoding(cec.getConfig()), new HashSet<Session>(), subProtocol, extensions, connectionBuilder);
        endpointInstance.onOpen(undertowSession, cec.getConfig());
        channel.resumeReceives();

        return undertowSession;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(final long timeout) {
        this.defaultMaxSessionIdleTimeout = timeout;
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
     * <p>
     * Unfortunately we need to dispatch to a thread pool, because there is a good chance that the endpoint
     * will use blocking IO methods. We suspend recieves while this is in progress, to make sure that we do not have multiple
     * methods invoked at once.
     * <p>
     *
     * @param invocation The task to run
     */
    public void invokeEndpointMethod(final Executor executor, final Runnable invocation) {
        if (dispatchToWorker) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeEndpointMethod(invocation);
                }
            });
        } else {
            invokeEndpointMethod(invocation);
        }
    }

    /**
     * Directly invokes an endpoint method, without dispatching to an executor
     * @param invocation The invocation
     */
    public void invokeEndpointMethod(final Runnable invocation) {
        ThreadSetupAction.Handle handle = threadSetupAction.setup(null);
        try {
            invocation.run();
        } finally {
            handle.tearDown();
        }
    }

    @Override
    public void addEndpoint(final Class<?> endpoint) throws DeploymentException {
        if (deploymentComplete) {
            throw JsrWebSocketMessages.MESSAGES.cannotAddEndpointAfterDeployment();
        }
        addEndpointInternal(endpoint, true);
    }

    private void addEndpointInternal(final Class<?> endpoint, boolean requiresCreation) throws DeploymentException {
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
            AnnotatedEndpointFactory annotatedEndpointFactory = AnnotatedEndpointFactory.create(endpoint, encodingFactory, template.getParameterNames());
            InstanceFactory<?> instanceFactory = null;
            try {
                instanceFactory = classIntrospecter.createInstanceFactory(endpoint);
            } catch (NoSuchMethodException e) {
                throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
            }
            Class<? extends ServerEndpointConfig.Configurator> configuratorClass = serverEndpoint.configurator();
            ServerEndpointConfig.Configurator configurator;
            if (configuratorClass != ServerEndpointConfig.Configurator.class) {
                try {
                    configurator = classIntrospecter.createInstanceFactory(configuratorClass).createInstance().getInstance();
                } catch (InstantiationException | NoSuchMethodException e) {
                    throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
                }
            } else {
                configurator = DefaultContainerConfigurator.INSTANCE;
            }

            ServerEndpointConfig config = ServerEndpointConfig.Builder.create(endpoint, serverEndpoint.value())
                    .decoders(Arrays.asList(serverEndpoint.decoders()))
                    .encoders(Arrays.asList(serverEndpoint.encoders()))
                    .subprotocols(Arrays.asList(serverEndpoint.subprotocols()))
                    .configurator(configurator)
                    .build();


            ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(config, instanceFactory, template, encodingFactory, annotatedEndpointFactory);
            configuredServerEndpoints.add(confguredServerEndpoint);
            handleAddingFilterMapping();
        } else if (clientEndpoint != null) {
            JsrWebSocketLogger.ROOT_LOGGER.addingAnnotatedClientEndpoint(endpoint);
            EncodingFactory encodingFactory = EncodingFactory.createFactory(classIntrospecter, clientEndpoint.decoders(), clientEndpoint.encoders());
            InstanceFactory<?> instanceFactory;
            try {
                instanceFactory = classIntrospecter.createInstanceFactory(endpoint);
            } catch (Exception e) {
                try {
                    instanceFactory = new ConstructorInstanceFactory<>(endpoint.getConstructor()); //this endpoint cannot be created by the container, the user will instantiate it
                } catch (NoSuchMethodException e1) {
                    if(requiresCreation) {
                        throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
                    } else {
                        instanceFactory = new InstanceFactory<Object>() {
                            @Override
                            public InstanceHandle<Object> createInstance() throws InstantiationException {
                                throw new InstantiationException();
                            }
                        };
                    }
                }
            }
            AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(endpoint, encodingFactory, Collections.<String>emptySet());

            ClientEndpointConfig.Configurator configurator = null;
            try {
                configurator = classIntrospecter.createInstanceFactory(clientEndpoint.configurator()).createInstance().getInstance();
            } catch (InstantiationException | NoSuchMethodException e) {
                throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
            }
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .decoders(Arrays.asList(clientEndpoint.decoders()))
                    .encoders(Arrays.asList(clientEndpoint.encoders()))
                    .preferredSubprotocols(Arrays.asList(clientEndpoint.subprotocols()))
                    .configurator(configurator)
                    .build();

            ConfiguredClientEndpoint configuredClientEndpoint = new ConfiguredClientEndpoint(config, factory, encodingFactory, instanceFactory);
            clientEndpoints.put(endpoint, configuredClientEndpoint);
        } else {
            throw JsrWebSocketMessages.MESSAGES.classWasNotAnnotated(endpoint);
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
        ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(endpoint, null, template, encodingFactory, null);
        configuredServerEndpoints.add(confguredServerEndpoint);
        handleAddingFilterMapping();
    }


    private ConfiguredClientEndpoint getClientEndpoint(final Class<?> endpointType, boolean requiresCreation) {
        Class<?> type = endpointType;
        while (type != Object.class && type != null && !type.isAnnotationPresent(ClientEndpoint.class)) {
            type = type.getSuperclass();
        }
        if(type == Object.class || type == null) {
            return null;
        }

        ConfiguredClientEndpoint existing = clientEndpoints.get(type);
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            existing = clientEndpoints.get(type);
            if (existing != null) {
                return existing;
            }
            if (type.isAnnotationPresent(ClientEndpoint.class)) {
                try {
                    addEndpointInternal(type, requiresCreation);
                    return clientEndpoints.get(type);
                } catch (DeploymentException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }
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

    @Override
    public synchronized void close() {
        for (ConfiguredServerEndpoint endpoint : configuredServerEndpoints) {
            for (Session session : endpoint.getOpenSessions()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, ""));
                } catch (Exception e) {
                    JsrWebSocketLogger.ROOT_LOGGER.couldNotCloseOnUndeploy(e);
                }
            }
        }
    }

    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    public XnioWorker getXnioWorker() {
        return xnioWorker;
    }

    private static List<WebSocketExtension> toExtensionList(final List<Extension> extensions) {
        List<WebSocketExtension> ret = new ArrayList<>();
        for (Extension e : extensions) {
            final List<WebSocketExtension.Parameter> parameters = new ArrayList<>();
            for (Extension.Parameter p : e.getParameters()) {
                parameters.add(new WebSocketExtension.Parameter(p.getName(), p.getValue()));
            }
            ret.add(new WebSocketExtension(e.getName(), parameters));
        }
        return ret;
    }

    private class ClientNegotiation extends WebSocketClientNegotiation {

        private final ClientEndpointConfig config;

        public ClientNegotiation(List<String> supportedSubProtocols, List<WebSocketExtension> supportedExtensions, ClientEndpointConfig config) {
            super(supportedSubProtocols, supportedExtensions);
            this.config = config;
        }

        @Override
        public void afterRequest(final Map<String, List<String>> headers) {

            ClientEndpointConfig.Configurator configurator = config.getConfigurator();
            if (configurator != null) {
                final Map<String, List<String>> newHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    arrayList.addAll(entry.getValue());
                    newHeaders.put(entry.getKey(), arrayList);
                }
                configurator.afterResponse(new HandshakeResponse() {
                    @Override
                    public Map<String, List<String>> getHeaders() {
                        return newHeaders;
                    }
                });
            }
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            ClientEndpointConfig.Configurator configurator = config.getConfigurator();
            if (configurator != null) {
                final Map<String, List<String>> newHeaders = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    arrayList.addAll(entry.getValue());
                    newHeaders.put(entry.getKey(), arrayList);
                }
                configurator.beforeRequest(newHeaders);
                headers.clear(); //TODO: more efficient way
                for (Map.Entry<String, List<String>> entry : newHeaders.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        headers.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    public WebSocketReconnectHandler getWebSocketReconnectHandler() {
        return webSocketReconnectHandler;
    }
}
