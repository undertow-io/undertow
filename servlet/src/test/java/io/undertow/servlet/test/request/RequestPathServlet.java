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

package io.undertow.servlet.test.request;

import java.io.IOException;
import java.net.URLDecoder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;

/**
 * @author Stuart Douglas
 */
public class RequestPathServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        StringBuilder builtUri = new StringBuilder(req.getContextPath());
        builtUri.append(req.getServletPath());
        builtUri.append(req.getPathInfo() == null ? "" : req.getPathInfo());
        Assert.assertEquals(URLDecoder.decode(req.getRequestURI(), "UTF-8"), builtUri.toString());

        resp.getWriter().write(req.getPathInfo() + ",");
        resp.getWriter().write(req.getServletPath() + ",");
        resp.getWriter().write(req.getRequestURL().toString() + ",");
        resp.getWriter().write(req.getRequestURI() + ",");
        resp.getWriter().write(req.getQueryString() == null ? "" : req.getQueryString());
    }
}
