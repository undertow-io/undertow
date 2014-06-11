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

import java.util.HashMap;
import java.util.Map;

import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.util.CopyOnWriteMap;

/**
 * Runtime representation of filters. Basically a container for {@link io.undertow.servlet.core.ManagedFilter} instances
 *
 * @author Stuart Douglas
 */
public class ManagedFilters {

    private final Map<String, ManagedFilter> managedFilterMap = new CopyOnWriteMap<>();
    private final DeploymentImpl deployment;
    private final ServletPathMatches servletPathMatches;

    public ManagedFilters(final DeploymentImpl deployment, final ServletPathMatches servletPathMatches) {
        this.deployment = deployment;
        this.servletPathMatches = servletPathMatches;
    }

    public ManagedFilter addFilter(final FilterInfo filterInfo) {
        ManagedFilter managedFilter = new ManagedFilter(filterInfo, deployment.getServletContext());
        managedFilterMap.put(filterInfo.getName(),managedFilter);
        deployment.addLifecycleObjects(managedFilter);
        servletPathMatches.invalidate();
        return managedFilter;
    }

    public ManagedFilter getManagedFilter(final String name) {
        return managedFilterMap.get(name);
    }

    public Map<String, ManagedFilter> getFilters() {
        return new HashMap<>(managedFilterMap);
    }

}
