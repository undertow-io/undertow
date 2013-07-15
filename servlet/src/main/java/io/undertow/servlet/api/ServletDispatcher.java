package io.undertow.servlet.api;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public interface ServletDispatcher {
    /**
     * Dispatches a servlet request to the specified servlet path, changing the current path
     * @see io.undertow.servlet.handlers.ServletRequestContext
     */
    void dispatchToPath(final HttpServerExchange exchange, final ServletPathMatch pathMatch, final DispatcherType dispatcherType) throws Exception;

    /**
     * Dispatches a servlet request to the specified servlet, without changing the current path
     */
    void dispatchToServlet(final HttpServerExchange exchange, final ServletChain servletChain, final DispatcherType dispatcherType) throws Exception;

    /**
     * Dispatches a mock request to the servlet container.
     *
     * @param request The request
     * @param response The response
     */
    void dispatchMockRequest(final HttpServletRequest request, final HttpServletResponse response) throws ServletException;
}
