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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;
import io.undertow.websockets.jsr.ConfiguredClientEndpoint;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.EndpointSessionHandler;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.JsrWebSocketLogger;
import io.undertow.websockets.jsr.JsrWebSocketMessages;
import io.undertow.websockets.jsr.PathTemplate;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;

/**
 * @author Stuart Douglas
 */
public class WebSocketDeployer {

    public static final String FILTER_NAME = "Undertow JSR-356 Websocket Filter";

    /**
     * Performs the deployment, verifying all endpoints and then installing the resulting deployment into the servlet
     * deployment.
     *
     * @param target The servlet deployment
     */
    public static void deploy(final WebSocketDeployment deployment, final DeploymentInfo target, final ClassLoader classLoader) throws DeploymentException {
        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader(); //we do not need a permission check, as non-privileged code should not be calling this method;
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            if (deployment.getDeploymentInfo().isEmpty()) {
                return;
            }

            Set<Class<?>> allAnnotatedEndpoints = new HashSet<>(deployment.getDeploymentInfo().getAnnotatedEndpoints());
            final Set<Class<? extends Endpoint>> allScannedEndpointImplementations = new HashSet<>(deployment.getDeploymentInfo().getDiscoveredEndpoints());

            final Set<ServerApplicationConfig> configInstances = new HashSet<>();
            for (Class<? extends ServerApplicationConfig> clazz : deployment.getDeploymentInfo().getServerApplicationConfigClass()) {
                try {
                    configInstances.add(clazz.newInstance());
                } catch (InstantiationException | IllegalAccessException e) {
                    JsrWebSocketLogger.ROOT_LOGGER.couldNotInitializeConfiguration(clazz, e);
                }
            }
            if (configInstances.isEmpty()
                    && allAnnotatedEndpoints.isEmpty()
                    && deployment.getDeploymentInfo().getProgramaticEndpoints().isEmpty()) {
                return;
            }

            final Set<ServerEndpointConfig> serverEndpointConfigurations = new HashSet<>(deployment.getDeploymentInfo().getProgramaticEndpoints());

            for (ServerApplicationConfig config : configInstances) {
                allAnnotatedEndpoints = config.getAnnotatedEndpointClasses(allAnnotatedEndpoints);
                serverEndpointConfigurations.addAll(config.getEndpointConfigs(allScannedEndpointImplementations));
            }

            //ok, now we have our endpoints, lets deploy them

            //thanks to the path template comparison function we can
            //test for duplicate end points via a tree set
            final TreeSet<PathTemplate> seenPaths = new TreeSet<>();

            final List<ConfiguredServerEndpoint> configuredServerEndpoints = new ArrayList<>();

            //annotated endpoints first
            try {
                for (Class<?> endpoint : allAnnotatedEndpoints) {
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
                        AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(endpoint, target.getClassIntrospecter().createInstanceFactory(endpoint));

                        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(endpoint, serverEndpoint.value())
                                .decoders(Arrays.asList(serverEndpoint.decoders()))
                                .encoders(Arrays.asList(serverEndpoint.encoders()))
                                .subprotocols(Arrays.asList(serverEndpoint.subprotocols()))
                                .configurator(new ServerInstanceFactoryConfigurator(factory))
                                .build();

                        ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(config, factory, template);
                        configuredServerEndpoints.add(confguredServerEndpoint);
                    } else if (clientEndpoint != null) {
                        AnnotatedEndpointFactory factory = AnnotatedEndpointFactory.create(endpoint, target.getClassIntrospecter().createInstanceFactory(endpoint));

                        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                                .decoders(Arrays.asList(clientEndpoint.decoders()))
                                .encoders(Arrays.asList(clientEndpoint.encoders()))
                                .preferredSubprotocols(Arrays.asList(clientEndpoint.subprotocols()))
                                .configurator(clientEndpoint.configurator().newInstance())
                                .build();

                        ConfiguredClientEndpoint configuredClientEndpoint = new ConfiguredClientEndpoint(config, factory);
                        deployment.addClientEndpoint(endpoint, configuredClientEndpoint);
                    } else {
                        throw JsrWebSocketMessages.MESSAGES.classWasNotAnnotated(endpoint);
                    }
                }
            } catch (NoSuchMethodException|InstantiationException|IllegalAccessException e) {
                throw JsrWebSocketMessages.MESSAGES.couldNotDeploy(e);
            }

            for (final ServerEndpointConfig endpoint : serverEndpointConfigurations) {
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
                ConfiguredServerEndpoint confguredServerEndpoint = new ConfiguredServerEndpoint(endpoint, null, template);
                configuredServerEndpoints.add(confguredServerEndpoint);
            }

            final JsrWebSocketFilter filter = new JsrWebSocketFilter(new WebSocketSessionConnectionCallback(new EndpointSessionHandler(deployment.getContainer())), configuredServerEndpoints);

            target.addFilter(new FilterInfo(FILTER_NAME, JsrWebSocketFilter.class, new ImmediateInstanceFactory<Filter>(filter))
                    .setAsyncSupported(true));
            target.addFilterUrlMapping(FILTER_NAME, "/*", DispatcherType.REQUEST);
            deployment.getContainer().deploymentComplete();

        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

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
