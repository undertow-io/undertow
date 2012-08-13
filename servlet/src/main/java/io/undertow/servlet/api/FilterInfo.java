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
public class FilterInfo {

    private final String filterClass;
    private final String name;
    private final InstanceFactory instanceFactory;
    private final List<String> mappings;

    FilterInfo(final String name, final String filterClass, final InstanceFactory instanceFactory, final List<String> mappings) {
        if (name == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("name");
        }
        if (filterClass == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("filterClass");
        }
        if (mappings == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("mappings");
        }

        this.name = name;
        this.filterClass = filterClass;
        this.instanceFactory = instanceFactory;
        this.mappings = Collections.unmodifiableList(new ArrayList<String>(mappings));

    }

    public String getName() {
        return name;
    }

    public String getFilterClass() {
        return filterClass;
    }

    public InstanceFactory getInstanceFactory() {
        return instanceFactory;
    }

    public List<String> getMappings() {
        return mappings;
    }

    public static FilterInfoBuilder builder() {
        return new FilterInfoBuilder();
    }

    public static class FilterInfoBuilder {
        private String filterClass;
        private String name;
        private InstanceFactory instanceFactory;
        private final List<String> mappings = new ArrayList<String>();

        FilterInfoBuilder() {

        }

        public FilterInfo build() {
            return new FilterInfo(name, filterClass,  instanceFactory, mappings);
        }

        public String getName() {
            return name;
        }

        public FilterInfoBuilder setName(final String name) {
            this.name = name;
            return this;
        }

        public String getFilterClass() {
            return filterClass;
        }

        public FilterInfoBuilder setFilterClass(final String filterClass) {
            this.filterClass = filterClass;
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

        public FilterInfoBuilder addMapping(final String mapping) {
            mappings.add(mapping);
            return this;
        }
    }
}
