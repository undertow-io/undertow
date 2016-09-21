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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletDispatcher;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Class that represents the mutable state associated with a servlet deployment that is built up
 * during the bootstrap process.
 * <p>
 * Classes calling deployment methods during bootstrap must be aware of ordering concerns.
 *
 * @author Stuart Douglas
 */
public class DeploymentImpl implements Deployment {

    private final DeploymentManager deploymentManager;
    private final DeploymentInfo deploymentInfo;
    private final ServletContainer servletContainer;
    private final List<Lifecycle> lifecycleObjects = new ArrayList<>();
    private final ServletPathMatches servletPaths;
    private final ManagedServlets servlets;
    private final ManagedFilters filters;


    private volatile ApplicationListeners applicationListeners;
    private volatile ServletContextImpl servletContext;
    private volatile ServletInitialHandler servletHandler;
    private volatile HttpHandler initialHandler;
    private volatile ErrorPages errorPages;
    private volatile Map<String, String> mimeExtensionMappings;
    private volatile SessionManager sessionManager;
    private volatile Charset defaultCharset;
    private volatile List<AuthenticationMechanism> authenticationMechanisms;
    private volatile List<ThreadSetupHandler> threadSetupActions;

    public DeploymentImpl(DeploymentManager deploymentManager, final DeploymentInfo deploymentInfo, ServletContainer servletContainer) {
        this.deploymentManager = deploymentManager;
        this.deploymentInfo = deploymentInfo;
        this.servletContainer = servletContainer;
        servletPaths = new ServletPathMatches(this);
        servlets = new ManagedServlets(this, servletPaths);
        filters = new ManagedFilters(this, servletPaths);
    }

    @Override
    public ServletContainer getServletContainer() {
        return servletContainer;
    }

    public ManagedServlets getServlets() {
        return servlets;
    }

    public ManagedFilters getFilters() {
        return filters;
    }

    void setApplicationListeners(final ApplicationListeners applicationListeners) {
        this.applicationListeners = applicationListeners;
    }

    void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public DeploymentInfo getDeploymentInfo() {
        return deploymentInfo;
    }

    @Override
    public ApplicationListeners getApplicationListeners() {
        return applicationListeners;
    }

    @Override
    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    @Override
    public HttpHandler getHandler() {
        return initialHandler;
    }

    public void setInitialHandler(final HttpHandler initialHandler) {
        this.initialHandler = initialHandler;
    }

    void setServletHandler(final ServletInitialHandler servletHandler) {
        this.servletHandler = servletHandler;
    }

    void addLifecycleObjects(final Collection<Lifecycle> objects) {
        lifecycleObjects.addAll(objects);
    }

    void addLifecycleObjects(final Lifecycle... objects) {
        lifecycleObjects.addAll(Arrays.asList(objects));
    }

    void setSessionManager(final SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public List<Lifecycle> getLifecycleObjects() {
        return Collections.unmodifiableList(lifecycleObjects);
    }

    @Override
    public ServletPathMatches getServletPaths() {
        return servletPaths;
    }

    void setThreadSetupActions(List<ThreadSetupHandler> threadSetupActions) {
        this.threadSetupActions = threadSetupActions;
    }

    public <C, T> ThreadSetupHandler.Action<C, T> createThreadSetupAction(ThreadSetupHandler.Action<C, T> target) {
        ThreadSetupHandler.Action<C, T> ret = target;
        for(ThreadSetupHandler wrapper : threadSetupActions) {
            ret = wrapper.create(ret);
        }
        return ret;
    }

    public ErrorPages getErrorPages() {
        return errorPages;
    }

    public void setErrorPages(final ErrorPages errorPages) {
        this.errorPages = errorPages;
    }

    @Override
    public Map<String, String> getMimeExtensionMappings() {
        return mimeExtensionMappings;
    }

    public void setMimeExtensionMappings(final Map<String, String> mimeExtensionMappings) {
        this.mimeExtensionMappings = Collections.unmodifiableMap(new HashMap<>(mimeExtensionMappings));
    }

    @Override
    public ServletDispatcher getServletDispatcher() {
        return servletHandler;
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public Executor getExecutor() {
        return deploymentInfo.getExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return deploymentInfo.getAsyncExecutor();
    }

    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    public void setAuthenticationMechanisms(List<AuthenticationMechanism> authenticationMechanisms) {
        this.authenticationMechanisms = authenticationMechanisms;
    }

    @Override
    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return authenticationMechanisms;
    }

    @Override
    public DeploymentManager.State getDeploymentState() {
        return deploymentManager.getState();
    }

    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    void destroy(){
        getApplicationListeners().contextDestroyed();
        getApplicationListeners().stop();
        if (servletContext!=null){
            servletContext.destroy();
        }
        servletContext = null;
    }
}
