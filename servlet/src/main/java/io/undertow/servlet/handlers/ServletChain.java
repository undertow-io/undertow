package io.undertow.servlet.handlers;

import java.util.concurrent.Executor;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ManagedServlet;

/**
* @author Stuart Douglas
*/
public class ServletChain {
    private final HttpHandler handler;
    private final ManagedServlet managedServlet;
    private final String servletPath;
    private final Executor executor;
    private final boolean defaultServletMapping;

    public ServletChain(final HttpHandler handler, final ManagedServlet managedServlet, final String servletPath, boolean defaultServletMapping) {
        this.handler = handler;
        this.managedServlet = managedServlet;
        this.servletPath = servletPath;
        this.defaultServletMapping = defaultServletMapping;
        this.executor = managedServlet.getServletInfo().getExecutor();
    }

    public ServletChain(final ServletChain other) {
        this(other.getHandler(), other.getManagedServlet(), other.getServletPath(), other.isDefaultServletMapping());
    }

    public HttpHandler getHandler() {
        return handler;
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }

    /**
     *
     * @return The servlet path part
     */
    public String getServletPath() {
        return servletPath;
    }

    public Executor getExecutor() {
        return executor;
    }

    public boolean isDefaultServletMapping() {
        return defaultServletMapping;
    }
}
