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

import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.LifecycleInterceptor;
import io.undertow.servlet.api.ServletInfo;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import java.util.List;

/**
 * Utility class for invoking servlet and filter lifecycle methods.
 */
class LifecyleInterceptorInvocation implements LifecycleInterceptor.LifecycleContext {
    private final List<LifecycleInterceptor> list;
    private final ServletInfo servletInfo;
    private final FilterInfo filterInfo;
    private final Servlet servlet;
    private final Filter filter;
    private int i;
    private final ServletConfig servletConfig;
    private final FilterConfig filterConfig;

    LifecyleInterceptorInvocation(List<LifecycleInterceptor> list, ServletInfo servletInfo, Servlet servlet,  ServletConfig servletConfig) {
        this.list = list;
        this.servletInfo = servletInfo;
        this.servlet = servlet;
        this.servletConfig = servletConfig;
        this.filter = null;
        this.filterConfig = null;
        this.filterInfo = null;
        i = list.size();
    }

    LifecyleInterceptorInvocation(List<LifecycleInterceptor> list, ServletInfo servletInfo, Servlet servlet) {
        this.list = list;
        this.servlet = servlet;
        this.servletInfo = servletInfo;
        this.filterInfo = null;
        this.servletConfig = null;
        this.filter = null;
        this.filterConfig = null;
        i = list.size();
    }

    LifecyleInterceptorInvocation(List<LifecycleInterceptor> list, FilterInfo filterInfo, Filter filter,  FilterConfig filterConfig) {
        this.list = list;
        this.servlet = null;
        this.servletConfig = null;
        this.filter = filter;
        this.filterConfig = filterConfig;
        this.filterInfo = filterInfo;
        this.servletInfo = null;
        i = list.size();
    }

    LifecyleInterceptorInvocation(List<LifecycleInterceptor> list, FilterInfo filterInfo, Filter filter) {
        this.list = list;
        this.servlet = null;
        this.servletConfig = null;
        this.filter = filter;
        this.filterConfig = null;
        this.filterInfo = filterInfo;
        this.servletInfo = null;
        i = list.size();
    }

    @Override
    public void proceed() throws ServletException {
        if (--i >= 0) {
            final LifecycleInterceptor next = list.get(i);
            if(filter != null) {
                if(filterConfig == null) {
                    next.destroy(filterInfo, filter, this);
                } else {
                    next.init(filterInfo, filter, this);
                }
            } else {
                if(servletConfig == null) {
                    next.destroy(servletInfo, servlet, this);
                } else {
                    next.init(servletInfo, servlet, this);
                }
            }
        } else if (i == -1) {
            if(filter != null) {
                if(filterConfig == null) {
                    filter.destroy();
                } else {
                    filter.init(filterConfig);
                }
            } else {
                if(servletConfig == null) {
                    servlet.destroy();
                } else {
                    servlet.init(servletConfig);
                }
            }
        }
    }
}
