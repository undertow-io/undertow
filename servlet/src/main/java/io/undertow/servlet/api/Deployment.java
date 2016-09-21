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

package io.undertow.servlet.api;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.core.ManagedFilters;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.ManagedServlets;
import io.undertow.servlet.core.ErrorPages;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Runtime representation of a deployment.
 *
 * @author Stuart Douglas
 */
public interface Deployment {

    DeploymentInfo getDeploymentInfo();

    ServletContainer getServletContainer();

    ApplicationListeners getApplicationListeners();

    ManagedServlets getServlets();

    ManagedFilters getFilters();

    ServletContextImpl getServletContext();

    HttpHandler getHandler();

    ServletPathMatches getServletPaths();

    <T, C> ThreadSetupHandler.Action<T, C> createThreadSetupAction(ThreadSetupHandler.Action<T, C> target);

    ErrorPages getErrorPages();

    Map<String, String> getMimeExtensionMappings();

    ServletDispatcher getServletDispatcher();

    /**
     *
     * @return The session manager
     */
    SessionManager getSessionManager();

    /**
     *
     * @return The executor used for servlet requests. May be null in which case the XNIO worker is used
     */
    Executor getExecutor();

    /**
     *
     * @return The executor used for async request dispatches. May be null in which case the XNIO worker is used
     */
    Executor getAsyncExecutor();

    Charset getDefaultCharset();

    /**
     *
     * @return The list of authentication mechanisms configured for this deployment
     */
    List<AuthenticationMechanism> getAuthenticationMechanisms();

    DeploymentManager.State getDeploymentState();

}
