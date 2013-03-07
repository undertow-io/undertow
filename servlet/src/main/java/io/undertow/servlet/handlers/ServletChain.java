package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ManagedServlet;

/**
* @author Stuart Douglas
*/
public class ServletChain {
    private final HttpHandler handler;
    private final ManagedServlet managedServlet;

    public ServletChain(final HttpHandler handler, final ManagedServlet managedServlet) {
        this.handler = handler;
        this.managedServlet = managedServlet;
    }

    public HttpHandler getHandler() {
        return handler;
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }
}
