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

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ContextClassLoaderSetupAction;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.connector.ByteBufferPool;
import org.xnio.XnioWorker;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class Bootstrap implements ServletExtension {

    public static final String FILTER_NAME = "Undertow Web Socket Filter";

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        WebSocketDeploymentInfo info = (WebSocketDeploymentInfo) deploymentInfo.getServletContextAttributes().get(WebSocketDeploymentInfo.ATTRIBUTE_NAME);

        if (info == null) {
            return;
        }
        XnioWorker worker = info.getWorker();
        if(worker == null) {
            JsrWebSocketLogger.ROOT_LOGGER.xnioWorkerWasNull();
            worker = ((ServerWebSocketContainer)ContainerProvider.getWebSocketContainer()).getXnioWorker();
        }
        ByteBufferPool buffers = info.getBuffers();
        if(buffers == null) {
            JsrWebSocketLogger.ROOT_LOGGER.bufferPoolWasNull();
            buffers = ((ServerWebSocketContainer)ContainerProvider.getWebSocketContainer()).getBufferPool();
        }

        final List<ThreadSetupAction> setup = new ArrayList<>();
        setup.add(new ContextClassLoaderSetupAction(deploymentInfo.getClassLoader()));
        setup.addAll(deploymentInfo.getThreadSetupActions());
        final CompositeThreadSetupAction threadSetupAction = new CompositeThreadSetupAction(setup);

        InetSocketAddress bind = null;
        if(info.getClientBindAddress() != null) {
            bind = new InetSocketAddress(info.getClientBindAddress(), 0);
        }

        ServerWebSocketContainer container = new ServerWebSocketContainer(deploymentInfo.getClassIntrospecter(), servletContext.getClassLoader(), worker, buffers, threadSetupAction, info.isDispatchToWorkerThread(), bind, info.getReconnectHandler());
        try {
            for (Class<?> annotation : info.getAnnotatedEndpoints()) {
                container.addEndpoint(annotation);
            }
            for(ServerEndpointConfig programatic : info.getProgramaticEndpoints()) {
                container.addEndpoint(programatic);
            }
        } catch (DeploymentException e) {
            throw new RuntimeException(e);
        }
        servletContext.setAttribute(ServerContainer.class.getName(), container);
        info.containerReady(container);
        SecurityActions.addContainer(deploymentInfo.getClassLoader(), container);

        deploymentInfo.addListener(Servlets.listener(WebSocketListener.class));
    }

    private static final class WebSocketListener implements ServletContextListener {

        private ServerWebSocketContainer container;

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            container = (ServerWebSocketContainer) sce.getServletContext().getAttribute(ServerContainer.class.getName());
            FilterRegistration.Dynamic filter = sce.getServletContext().addFilter(FILTER_NAME, JsrWebSocketFilter.class);
            filter.setAsyncSupported(true);
            if(!container.getConfiguredServerEndpoints().isEmpty()){
                filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
            } else {
                container.setContextToAddFilter((ServletContextImpl) sce.getServletContext());
            }
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            SecurityActions.removeContainer(sce.getServletContext().getClassLoader());
            container.close();
        }
    }

}
