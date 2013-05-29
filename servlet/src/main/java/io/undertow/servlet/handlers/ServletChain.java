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

    public ServletChain(final HttpHandler handler, final ManagedServlet managedServlet, final String servletPath) {
        this.handler = handler;
        this.managedServlet = managedServlet;
        this.servletPath = servletPath;
        this.executor = managedServlet.getServletInfo().getExecutor();
    }

    public ServletChain(final ServletChain other) {
        this(other.getHandler(), other.getManagedServlet(), other.getServletPath());
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
}
