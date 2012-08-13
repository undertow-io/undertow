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

package io.undertow.servlet.handlers;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceHandle;

/**
 * @author Stuart Douglas
 */
public class ManagedFilter {

    private final FilterInfo filterInfo;
    private final Class<?> filterClass;

    private volatile boolean started = false;
    private volatile Filter filter;
    private volatile InstanceHandle handle;

    public ManagedFilter(final FilterInfo filterInfo, final Class<?> filterClass) {
        this.filterInfo = filterInfo;
        this.filterClass = filterClass;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!started) {
            start();
        }
        filter.doFilter(request, response, chain);
    }

    public synchronized void start() {
        if (!started) {
            if (filterInfo.getInstanceFactory() != null) {
                handle = filterInfo.getInstanceFactory().createInstance();
                filter = (Filter) handle.getInstance();
            } else {
                try {
                    filter = (Filter) filterClass.newInstance();
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            started = true;
        }
    }

    public synchronized void stop() {
        started = false;
        if (handle != null) {
            handle.release();
        }
    }
}
