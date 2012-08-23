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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.util.ConstructorInstanceFactory;

/**
 * @author Stuart Douglas
 */
public class FilterInfo {

    private final Class<? extends Filter> filterClass;
    private final String name;
    private volatile InstanceFactory instanceFactory;

    private final List<Mapping> mappings = new ArrayList<Mapping>();
    private final Map<String, String> initParams = new HashMap<String, String>();
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
            final Constructor<?> ctor = filterClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            this.instanceFactory = new ConstructorInstanceFactory(ctor);
            this.name = name;
            this.filterClass = filterClass;
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.componentMustHaveDefaultConstructor("Filter", filterClass);
        }
    }


    public FilterInfo(final String name, final Class<? extends Filter> filterClass, final InstanceFactory instanceFactory) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (filterClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("filterClass", "Filter", name);
        }
        if (!Servlet.class.isAssignableFrom(filterClass)) {
            throw UndertowServletMessages.MESSAGES.filterMustImplementFilter(name, filterClass);
        }
        this.instanceFactory = instanceFactory;
        this.name = name;
        this.filterClass = filterClass;
    }

    public void validate() {
        //TODO
    }

    public FilterInfo copy() {
        FilterInfo info = new FilterInfo(name, filterClass, instanceFactory)
                .setAsyncSupported(asyncSupported);
        info.mappings.addAll(mappings);
        info.initParams.putAll(initParams);
        return info;
    }

    public Class<? extends Filter> getFilterClass() {
        return filterClass;
    }

    public String getName() {
        return name;
    }

    public void setInstanceFactory(final InstanceFactory instanceFactory) {
        if(instanceFactory == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("instanceFactory");
        }
        this.instanceFactory = instanceFactory;
    }

    public InstanceFactory getInstanceFactory() {
        return instanceFactory;
    }

    public List<Mapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    public FilterInfo addUrlMapping(final String mapping) {
        mappings.add(new Mapping(MappingType.URL, mapping, null));
        return this;
    }

    public FilterInfo addUrlMapping(final String mapping, DispatcherType dispatcher) {
        mappings.add(new Mapping(MappingType.URL, mapping, dispatcher));
        return this;
    }

    public FilterInfo addServletNameMapping(final String mapping) {
        mappings.add(new Mapping(MappingType.SERVLET, mapping, null));
        return this;
    }

    public FilterInfo addServletNameMapping(final String mapping, final DispatcherType dispatcher) {
        mappings.add(new Mapping(MappingType.SERVLET, mapping, dispatcher));
        return this;
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


    public static enum MappingType {
        URL,
        SERVLET;
    }

    public static class Mapping {
        private final MappingType mappingType;
        private final String mapping;
        private final DispatcherType dispatcher;

        public Mapping(final MappingType mappingType, final String mapping, final DispatcherType dispatcher) {
            this.mappingType = mappingType;
            this.mapping = mapping;
            this.dispatcher = dispatcher;
        }

        public MappingType getMappingType() {
            return mappingType;
        }

        public String getMapping() {
            return mapping;
        }

        public DispatcherType getDispatcher() {
            return dispatcher;
        }
    }

}
