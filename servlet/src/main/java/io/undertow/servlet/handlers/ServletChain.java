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

package io.undertow.servlet.handlers;

import java.util.concurrent.Executor;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ManagedServlet;

import javax.servlet.http.MappingMatch;

/**
* @author Stuart Douglas
*/
public class ServletChain {
    private final HttpHandler handler;
    private final ManagedServlet managedServlet;
    private final String servletPath;
    private final Executor executor;
    private final boolean defaultServletMapping;
    private final MappingMatch mappingMatch;
    private final String pattern;

    public ServletChain(final HttpHandler handler, final ManagedServlet managedServlet, final String servletPath, boolean defaultServletMapping, MappingMatch mappingMatch, String pattern) {
        this.handler = handler;
        this.managedServlet = managedServlet;
        this.servletPath = servletPath;
        this.defaultServletMapping = defaultServletMapping;
        this.mappingMatch = mappingMatch;
        this.pattern = pattern;
        this.executor = managedServlet.getServletInfo().getExecutor();
    }

    public ServletChain(final ServletChain other, String pattern, MappingMatch mappingMatch) {
        this(other.getHandler(), other.getManagedServlet(), other.getServletPath(), other.isDefaultServletMapping(), mappingMatch, pattern);
    }

    public HttpHandler getHandler() {
        return handler;
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }

    /**
     *
     * @return The servlet path part
     */
    public String getServletPath() {
        return servletPath;
    }

    public Executor getExecutor() {
        return executor;
    }

    public boolean isDefaultServletMapping() {
        return defaultServletMapping;
    }

    public MappingMatch getMappingMatch() {
        return mappingMatch;
    }

    public String getPattern() {
        return pattern;
    }
}
