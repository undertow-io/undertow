package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ManagedServlet;

/**
* @author Stuart Douglas
*/
public class ServletChain {
    private final HttpHandler handler;
    private final ManagedServlet managedServlet;
    private final boolean defaultServlet;

    public ServletChain(final HttpHandler handler, final ManagedServlet managedServlet, final boolean defaultServlet) {
        this.handler = handler;
        this.managedServlet = managedServlet;
        this.defaultServlet = defaultServlet;
    }

    public ServletChain(final ServletChain other) {
        this(other.getHandler(), other.getManagedServlet(), other.isDefaultServlet());
    }
    public HttpHandler getHandler() {
        return handler;
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }

    public boolean isDefaultServlet() {
        return defaultServlet;
    }
}
