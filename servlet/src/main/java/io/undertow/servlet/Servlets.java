package io.undertow.servlet;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.core.ServletContainerImpl;

/**
 * Utility class for building servlet deployments.
 *
 * @author Stuart Douglas
 */
public class Servlets {

    private static volatile ServletContainer container;

    /**
     * Returns the default servlet container. For most embedded use
     * cases this will be sufficient.
     *
     * @return The default servlet container
     */
    public static ServletContainer defaultContainer() {
        if (container != null) {
            return container;
        }
        synchronized (Servlets.class) {
            if (container != null) {
                return container;
            }
            return container = ServletContainer.Factory.newInstance();
        }
    }

    /**
     * Creates a new servlet container.
     *
     * @return A new servlet container
     */
    public static ServletContainer newContainer() {
        return new ServletContainerImpl();
    }

    /**
     * Creates a new servlet deployment info structure
     *
     * @return A new deployment info structure
     */
    public static DeploymentInfo deployment() {
        return new DeploymentInfo();
    }

    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name         The servlet name
     * @param servletClass The servlet class
     * @return A new servlet description
     */
    public static ServletInfo servlet(final String name, final Class<? extends Servlet> servletClass) {
        return new ServletInfo(name, servletClass);
    }

    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name         The servlet name
     * @param servletClass The servlet class
     * @return A new servlet description
     */
    public static final ServletInfo servlet(final String name, final Class<? extends Servlet> servletClass, final InstanceFactory<? extends Servlet> servlet) {
        return new ServletInfo(name, servletClass, servlet);
    }


    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name        The filter name
     * @param filterClass The filter class
     * @return A new servlet description
     */
    public static FilterInfo filter(final String name, final Class<? extends Filter> filterClass) {
        return new FilterInfo(name, filterClass);
    }

    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name        The filter name
     * @param filterClass The filter class
     * @return A new filter description
     */
    public static final FilterInfo filter(final String name, final Class<? extends Filter> filterClass, final InstanceFactory<? extends Filter> filter) {
        return new FilterInfo(name, filterClass, filter);
    }

    private Servlets() {
    }

}
