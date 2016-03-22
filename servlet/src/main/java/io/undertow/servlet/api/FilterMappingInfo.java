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

import javax.servlet.DispatcherType;

/**
 * @author Stuart Douglas
 */
public class FilterMappingInfo {

    private final String filterName;
    private final MappingType mappingType;
    private final String mapping;
    private final DispatcherType dispatcher;

    public FilterMappingInfo(final String filterName, final MappingType mappingType, final String mapping, final DispatcherType dispatcher) {
        this.filterName = filterName;
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

    public String getFilterName() {
        return filterName;
    }

    public enum MappingType {
        URL,
        SERVLET;
    }

}
