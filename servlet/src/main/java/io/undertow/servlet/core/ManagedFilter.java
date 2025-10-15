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

package io.undertow.servlet.core;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.spec.FilterConfigImpl;
import io.undertow.servlet.spec.ServletContextImpl;

import static org.xnio.Bits.anyAreSet;

/**
 * @author Stuart Douglas
 */
public class ManagedFilter implements Lifecycle {

    private final FilterInfo filterInfo;
    private final ServletContextImpl servletContext;

    private static final int FLAG_STARTED = 1;
    private static final int FLAG_STOPPED = 1 << 1;
    @SuppressWarnings("unused")
    private volatile int state;
    private static final AtomicIntegerFieldUpdater<ManagedFilter> stateFieldUpdater = AtomicIntegerFieldUpdater.newUpdater(ManagedFilter.class, "state");

    private volatile Filter filter;
    private volatile InstanceHandle<? extends Filter> handle;

    public ManagedFilter(final FilterInfo filterInfo, final ServletContextImpl servletContext) {
        this.filterInfo = filterInfo;
        this.servletContext = servletContext;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(servletContext.getDeployment().getDeploymentState() != DeploymentManager.State.STARTED) {
            throw UndertowServletMessages.MESSAGES.deploymentStopped(servletContext.getDeployment().getDeploymentInfo().getDeploymentName());
        }
        start();
        getFilter().doFilter(request, response, chain);
    }

    private Filter getFilter() throws ServletException {
        if (filter == null) {
            createFilter();
        }
        return filter;
    }

    public void createFilter() throws ServletException {
        synchronized (this) {
            if (filter == null) {
                try {
                    handle = filterInfo.getInstanceFactory().createInstance();
                } catch (Exception e) {
                    throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(filterInfo.getName(), e);
                }
                Filter filter = handle.getInstance();
                new LifecyleInterceptorInvocation(servletContext.getDeployment().getDeploymentInfo().getLifecycleInterceptors(), filterInfo, filter, new FilterConfigImpl(filterInfo, servletContext)).proceed();
                this.filter = filter;
            }
        }
    }

    public void start() throws ServletException {
        do {
            if (anyAreSet(stateFieldUpdater.get(this), FLAG_STOPPED)) {
                throw UndertowServletMessages.MESSAGES.deploymentStopped(servletContext.getDeployment().getDeploymentInfo().getDeploymentName());
            }
        } while (stateFieldUpdater.get(this) != FLAG_STARTED && !stateFieldUpdater.compareAndSet(this, 0, FLAG_STARTED));
    }

    public void stop() {
        stateFieldUpdater.set(this, FLAG_STOPPED);
        synchronized (this) {
            if (handle != null) {
                try {
                    new LifecyleInterceptorInvocation(servletContext.getDeployment().getDeploymentInfo().getLifecycleInterceptors(), filterInfo, filter).proceed();
                } catch (Exception e) {
                    UndertowServletLogger.ROOT_LOGGER.failedToDestroy(filterInfo, e);
                }
                handle.release();
            }
            filter = null;
            handle = null;
        }
    }

    @Override
    public boolean isStarted() {
        return anyAreSet(state, FLAG_STARTED);
    }

    public FilterInfo getFilterInfo() {
        return filterInfo;
    }

    @Override
    public String toString() {
        return "ManagedFilter{" +
                "filterInfo=" + filterInfo +
                '}';
    }

    public void forceInit() throws ServletException {
        if (filter == null) {
            createFilter();
        }
    }
}
