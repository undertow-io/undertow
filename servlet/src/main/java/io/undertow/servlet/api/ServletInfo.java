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
import java.util.List;

import io.undertow.servlet.UndertowServletMessages;

/**
 * @author Stuart Douglas
 */
public class ServletInfo {

    private final String servletClass;
    private final String name;
    private final InstanceFactory instanceFactory;
    private final List<String> mappings;

    ServletInfo(final String servletClass, final String name, final InstanceFactory instanceFactory, final List<String> mappings) {
        if (servletClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("servletClass");
        }
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (mappings == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("mappings");
        }

        this.servletClass = servletClass;
        this.name = name;
        this.instanceFactory = instanceFactory;
        this.mappings = Collections.unmodifiableList(new ArrayList<String>(mappings));

    }

    public String getServletClass() {
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

    public ServletInfoBuilder builder() {
        return new ServletInfoBuilder();
    }

    public static class ServletInfoBuilder {
        private String servletClass;
        private String name;
        private InstanceFactory instanceFactory;
        private final List<String> mappings = new ArrayList<String>();

        ServletInfoBuilder() {

        }

        public ServletInfo build() {
            return new ServletInfo(servletClass, name, instanceFactory, mappings);
        }

        public String getName() {
            return name;
        }

        public ServletInfoBuilder setName(final String name) {
            this.name = name;
            return this;
        }

        public String getServletClass() {
            return servletClass;
        }

        public ServletInfoBuilder setServletClass(final String servletClass) {
            this.servletClass = servletClass;
            return this;
        }

        public InstanceFactory getInstanceFactory() {
            return instanceFactory;
        }

        public void setInstanceFactory(final InstanceFactory instanceFactory) {
            this.instanceFactory = instanceFactory;
        }

        public List<String> getMappings() {
            return mappings;
        }
    }
}
