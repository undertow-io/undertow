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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;

import io.undertow.servlet.UndertowServletMessages;

/**
 * @author Stuart Douglas
 */
public class ServletInfo {

    private final Class<? extends Servlet> servletClass;
    private final String name;
    private final InstanceFactory instanceFactory;
    private final List<String> mappings;
    private final Map<String, String> initParams;

    ServletInfo(final Class<? extends Servlet> servletClass, final String name, final InstanceFactory instanceFactory, final List<String> mappings, final Map<String, String> initParams) {
        if (servletClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("servletClass");
        }
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (mappings == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("mappings");
        }
        if (initParams == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("initParams");
        }
        this.servletClass = servletClass;
        this.name = name;
        this.instanceFactory = instanceFactory;
        this.mappings = Collections.unmodifiableList(new ArrayList<String>(mappings));
        this.initParams = Collections.unmodifiableMap(new LinkedHashMap<String, String>(initParams));

    }

    public Class<? extends Servlet> getServletClass() {
        return servletClass;
    }

    public String getName() {
        return name;
    }

    public InstanceFactory getInstanceFactory() {
        return instanceFactory;
    }

    public List<String> getMappings() {
        return mappings;
    }

    public Map<String, String> getInitParams() {
        return initParams;
    }

    public static ServletInfoBuilder builder() {
        return new ServletInfoBuilder();
    }

    public static class ServletInfoBuilder {
        private Class<? extends Servlet> servletClass;
        private String name;
        private InstanceFactory instanceFactory;
        private final List<String> mappings = new ArrayList<String>();
        private final Map<String, String> initParams = new LinkedHashMap<String, String>();

        ServletInfoBuilder() {

        }

        public ServletInfoBuilder clone() {
            ServletInfoBuilder n = new ServletInfoBuilder();
            n.setServletClass(servletClass)
                    .setName(name)
                    .setInstanceFactory(instanceFactory)
                    .getMappings().addAll(mappings);
            n.getInitParams().putAll(initParams);
            return n;
        }

        public ServletInfo build() {
            return new ServletInfo(servletClass, name, instanceFactory, mappings, initParams);
        }

        public String getName() {
            return name;
        }

        public ServletInfoBuilder setName(final String name) {
            this.name = name;
            return this;
        }

        public Class<? extends Servlet> getServletClass() {
            return servletClass;
        }

        public ServletInfoBuilder setServletClass(final Class<? extends Servlet> servletClass) {
            this.servletClass = servletClass;
            return this;
        }

        public InstanceFactory getInstanceFactory() {
            return instanceFactory;
        }

        public ServletInfoBuilder setInstanceFactory(final InstanceFactory instanceFactory) {
            this.instanceFactory = instanceFactory;
            return this;
        }

        public List<String> getMappings() {
            return mappings;
        }

        public ServletInfoBuilder addMapping(final String mapping) {
            mappings.add(mapping);
            return this;
        }

        public Map<String, String> getInitParams() {
            return initParams;
        }


    }
}
