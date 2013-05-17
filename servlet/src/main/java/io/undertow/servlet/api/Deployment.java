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

package io.undertow.servlet.api;

import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ErrorPages;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * @author Stuart Douglas
 */
public interface Deployment {

    DeploymentInfo getDeploymentInfo();

    ApplicationListeners getApplicationListeners();

    ServletContextImpl getServletContext();

    HttpHandler getHandler();

    ServletPathMatches getServletPaths();

    CompositeThreadSetupAction getThreadSetupAction();

    ErrorPages getErrorPages();

    Map<String, String> getMimeExtensionMappings();

    ServletDispatcher getServletDispatcher();

    SessionManager getSessionManager();
}
