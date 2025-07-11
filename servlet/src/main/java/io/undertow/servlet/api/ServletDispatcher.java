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
    void dispatchToPath(HttpServerExchange exchange, ServletPathMatch pathMatch, DispatcherType dispatcherType) throws Exception;

    /**
     * Dispatches a servlet request to the specified servlet, without changing the current path
     */
    void dispatchToServlet(HttpServerExchange exchange, ServletChain servletChain, DispatcherType dispatcherType) throws Exception;

    /**
     * Dispatches a mock request to the servlet container.
     *
     * @param request The request
     * @param response The response
     */
    void dispatchMockRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException;
}
