package io.undertow.servlet.api;

import javax.servlet.DispatcherType;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;

/**
 * @author Stuart Douglas
 */
public interface ServletDispatcher {
    /**
     * Dispatches a servlet request to the specified servlet path, changing the current path
     * @see io.undertow.servlet.handlers.ServletAttachments#SERVLET_PATH_MATCH
     */
    void dispatchToPath(final HttpServerExchange exchange, final ServletPathMatch pathMatch, final DispatcherType dispatcherType) throws Exception;

    /**
     * Dispatches a servlet request to the specified servlet, without changing the current path
     */
    void dispatchToServlet(final HttpServerExchange exchange, final ServletChain servletChain, final DispatcherType dispatcherType) throws Exception;

}
