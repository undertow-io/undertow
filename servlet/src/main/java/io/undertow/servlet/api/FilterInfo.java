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

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.util.ConstructorInstanceFactory;

/**
 * @author Stuart Douglas
 */
public class FilterInfo implements Cloneable {

    private final Class<? extends Filter> filterClass;
    private final String name;
    private volatile InstanceFactory<? extends Filter> instanceFactory;

    private final Map<String, String> initParams = new HashMap<>();
    private volatile boolean asyncSupported;


    public FilterInfo(final String name, final Class<? extends Filter> filterClass) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (filterClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("filterClass", "Filter", name);
        }
        if (!Filter.class.isAssignableFrom(filterClass)) {
            throw UndertowServletMessages.MESSAGES.filterMustImplementFilter(name, filterClass);
        }
        try {
            final Constructor<Filter> ctor = (Constructor<Filter>) filterClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            this.instanceFactory = new ConstructorInstanceFactory<>(ctor);
            this.name = name;
            this.filterClass = filterClass;
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.componentMustHaveDefaultConstructor("Filter", filterClass);
        }
    }


    public FilterInfo(final String name, final Class<? extends Filter> filterClass, final InstanceFactory<? extends Filter> instanceFactory) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (filterClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("filterClass", "Filter", name);
        }
        if (!Filter.class.isAssignableFrom(filterClass)) {
            throw UndertowServletMessages.MESSAGES.filterMustImplementFilter(name, filterClass);
        }
        this.instanceFactory = instanceFactory;
        this.name = name;
        this.filterClass = filterClass;
    }

    public void validate() {
        //TODO
    }

    @Override
    public FilterInfo clone() {
        FilterInfo info = new FilterInfo(name, filterClass, instanceFactory)
                .setAsyncSupported(asyncSupported);
        info.initParams.putAll(initParams);
        return info;
    }

    public Class<? extends Filter> getFilterClass() {
        return filterClass;
    }

    public String getName() {
        return name;
    }
    public InstanceFactory<? extends Filter> getInstanceFactory() {
        return instanceFactory;
    }

    public void setInstanceFactory(InstanceFactory<? extends Filter> instanceFactory) {
        this.instanceFactory = instanceFactory;
    }

    public FilterInfo addInitParam(final String name, final String value) {
        initParams.put(name, value);
        return this;
    }

    public Map<String, String> getInitParams() {
        return Collections.unmodifiableMap(initParams);
    }

    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    public FilterInfo setAsyncSupported(final boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
        return this;
    }

    @Override
    public String toString() {
        return "FilterInfo{" +
                "filterClass=" + filterClass +
                ", name='" + name + '\'' +
                '}';
    }
}
