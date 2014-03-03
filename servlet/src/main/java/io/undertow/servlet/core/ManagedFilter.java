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

package io.undertow.servlet.core;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.spec.FilterConfigImpl;

/**
 * @author Stuart Douglas
 */
public class ManagedFilter implements Lifecycle {

    private final FilterInfo filterInfo;
    private final ServletContext servletContext;

    private volatile boolean started = false;
    private volatile Filter filter;
    private volatile InstanceHandle<? extends Filter> handle;

    public ManagedFilter(final FilterInfo filterInfo, final ServletContext servletContext) {
        this.filterInfo = filterInfo;
        this.servletContext = servletContext;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!started) {
            start();
        }
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
                filter.init(new FilterConfigImpl(filterInfo, servletContext));
                this.filter = filter;
            }
        }
    }

    public synchronized void start() throws ServletException {
        if (!started) {

            started = true;
        }
    }

    public synchronized void stop() {
        started = false;
        if (handle != null) {
            filter.destroy();
            handle.release();
        }
        filter = null;
        handle = null;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public FilterInfo getFilterInfo() {
        return filterInfo;
    }
}
