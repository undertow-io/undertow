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

package io.undertow.servlet.sse;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.spec.ServletContextImpl;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HandlesTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
@HandlesTypes(ServerSentEvent.class)
public class ServerSentEventSCI implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        if(c == null || c.isEmpty()) {
            return;
        }
        try {
            final Map<String, ServerSentEventConnectionCallback> callbacks = new HashMap<>();
            ServletContextImpl servletContext = (ServletContextImpl) ctx;
            final List<InstanceHandle<?>> handles = new ArrayList<>();
            for (Class<?> clazz : c) {
                final ServerSentEvent annotation = clazz.getAnnotation(ServerSentEvent.class);
                if(annotation == null) {
                    continue;
                }
                String path = annotation.value();
                final InstanceHandle<?> instance = servletContext.getDeployment().getDeploymentInfo().getClassIntrospecter().createInstanceFactory(clazz).createInstance();
                handles.add(instance);
                callbacks.put(path, (ServerSentEventConnectionCallback) instance.getInstance());

            }
            if(callbacks.isEmpty()) {
                return;
            }

            servletContext.getDeployment().getDeploymentInfo().addInnerHandlerChainWrapper(new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    PathTemplateHandler pathTemplateHandler = new PathTemplateHandler(handler, false);
                    for(Map.Entry<String, ServerSentEventConnectionCallback> e : callbacks.entrySet()) {
                        pathTemplateHandler.add(e.getKey(), new ServerSentEventHandler(e.getValue()));
                    }
                    return pathTemplateHandler;
                }
            });
            servletContext.addListener(new ServletContextListener() {
                @Override
                public void contextInitialized(ServletContextEvent sce) {

                }

                @Override
                public void contextDestroyed(ServletContextEvent sce) {
                    for(InstanceHandle<?> h: handles) {
                        h.release();
                    }
                }
            });
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
