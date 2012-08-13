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

package io.undertow.servlet.deployment;

import java.lang.reflect.Method;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletInvocationHandler;

/**
 * Internal representation of a servlet deployment. This is not a
 *
 * @author Stuart Douglas
 */
public class DeploymentManagerImpl implements DeploymentManager {

    private final DeploymentInfo deployment;
    private final PathHandler pathHandler;
    private volatile State state = State.UNDEPLOYED;
    private volatile InstanceHandle servletInstance;

    public DeploymentManagerImpl(final DeploymentInfo deployment, final PathHandler pathHandler) {
        this.deployment = deployment;
        this.pathHandler = pathHandler;
    }

    @Override
    public void deploy() {
        //TODO: this is just a temporary hack
        //the real code should be nothing like this
        try {
            final PathHandler servletHandler = new PathHandler();
            pathHandler.addPath(deployment.getContextName(), servletHandler);

            for (Map.Entry<String, ServletInfo> entry : deployment.getServlets().entrySet()) {
                ServletInfo servlet = entry.getValue();
                InstanceFactory factory = servlet.getInstanceFactory();
                final Object instance;
                if(factory == null) {
                    Class<?> instanceClass = Class.forName(servlet.getServletClass(), false, deployment.getClassLoader());
                    instance = instanceClass.newInstance();
                } else {
                    instance = factory.createInstance().getInstance();
                }

                final Method method = Servlet.class.getMethod("service", ServletRequest.class, ServletResponse.class);
                final ServletInvocationHandler handler = new ServletInvocationHandler(instance, method);
                final ServletInitialHandler initial = new ServletInitialHandler(handler);

                for(final String mapping : servlet.getMappings()) {
                    final BlockingHandler blockingHandler = new BlockingHandler();
                    blockingHandler.setRootHandler(initial);
                    pathHandler.addPath(mapping, blockingHandler);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {

    }

    @Override
    public void undeploy() {

    }

    @Override
    public State getState() {
        return state;
    }
}
