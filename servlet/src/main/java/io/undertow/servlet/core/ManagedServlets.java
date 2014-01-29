package io.undertow.servlet.core;

import java.util.HashMap;
import java.util.Map;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.util.CopyOnWriteMap;

/**
 * Runtime representation of servlets. Basically a container for {@link ManagedServlet} instances
 *
 * @author Stuart Douglas
 */
public class ManagedServlets {

    private final Map<String, ServletHandler> managedServletMap = new CopyOnWriteMap<String, ServletHandler>();
    private final DeploymentImpl deployment;
    private final ServletPathMatches servletPaths;

    public ManagedServlets(final DeploymentImpl deployment, final ServletPathMatches servletPaths) {
        this.deployment = deployment;
        this.servletPaths = servletPaths;
    }

    public ServletHandler addServlet(final ServletInfo servletInfo) {
        ManagedServlet managedServlet = new ManagedServlet(servletInfo, deployment.getServletContext());
        ServletHandler servletHandler = new ServletHandler(managedServlet);
        managedServletMap.put(servletInfo.getName(), servletHandler);
        deployment.addLifecycleObjects(managedServlet);
        this.servletPaths.invalidate();

        return servletHandler;
    }

    public ManagedServlet getManagedServlet(final String name) {
        ServletHandler servletHandler = managedServletMap.get(name);
        if(servletHandler == null) {
            return null;
        }
        return servletHandler.getManagedServlet();
    }

    public ServletHandler getServletHandler(final String name) {
        return managedServletMap.get(name);
    }

    public Map<String, ServletHandler> getServletHandlers() {
        return new HashMap<String, ServletHandler>(managedServletMap);
    }

}
