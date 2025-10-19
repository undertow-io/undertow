/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.async;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author rmartinc
 */
public class AsyncDispatchPathTestServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try ( PrintWriter out = resp.getWriter()) {
            out.print("pathInfo:" + req.getPathInfo());
            out.print(" queryString:" + req.getQueryString());
            out.print(" servletPath:" + req.getServletPath());
            out.println(" requestUri:" + req.getRequestURI());

            out.println("jakarta.servlet.async.request_uri:" + req.getAttribute("jakarta.servlet.async.request_uri"));
            out.println("jakarta.servlet.async.context_path:" + req.getAttribute("jakarta.servlet.async.context_path"));
            out.println("jakarta.servlet.async.servlet_path:" + req.getAttribute("jakarta.servlet.async.servlet_path"));
            out.println("jakarta.servlet.async.path_info:" + req.getAttribute("jakarta.servlet.async.path_info"));
            out.println("jakarta.servlet.async.query_string:" + req.getAttribute("jakarta.servlet.async.query_string"));
        }
    }
}
