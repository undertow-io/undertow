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
public class Filters {

    private final Map<String, ManagedFilter> managedFilterMap = new CopyOnWriteMap<>();
    private final DeploymentImpl deployment;
    private final ServletPathMatches servletPathMatches;

    public Filters(final DeploymentImpl deployment, final ServletPathMatches servletPathMatches) {
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
