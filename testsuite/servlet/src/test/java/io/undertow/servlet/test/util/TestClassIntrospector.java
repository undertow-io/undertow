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

package io.undertow.servlet.test.util;

import java.util.EventListener;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletInfo;

/**
 * @author Stuart Douglas
 */
public class TestClassIntrospector implements ClassIntrospecter {

    public static final TestClassIntrospector INSTANCE = new TestClassIntrospector();

    @Override
    public ServletInfo createServletInfo(final String name, final Class<? extends Servlet> servlet) {
        return new ServletInfo(name, servlet);
    }

    @Override
    public FilterInfo createFilterInfo(final String name, final Class<? extends Filter> filter) {
        return new FilterInfo(name, filter);
    }

    @Override
    public ListenerInfo createListenerInfo(final Class<? extends EventListener> listener) {
        return new ListenerInfo(listener);
    }
}
