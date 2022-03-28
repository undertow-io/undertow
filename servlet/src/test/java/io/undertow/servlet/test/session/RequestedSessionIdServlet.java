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

package io.undertow.servlet.test.session;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class RequestedSessionIdServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        switch (req.getParameter("action")) {
            case "create":
                req.getSession(true);
                resp.getWriter().write(req.getRequestedSessionId());
                break;
            case "destroy":
                req.getSession().invalidate();
                resp.getWriter().write(req.getRequestedSessionId());
                break;
            case "destroycreate":
                req.getSession().invalidate();
                req.getSession(true);
                resp.getWriter().write(req.getRequestedSessionId());
                break;
            case "change":
                req.changeSessionId();
                resp.getWriter().write(req.getRequestedSessionId());
                break;
            case "timeout":
                req.getSession(true).setMaxInactiveInterval(1);
                resp.getWriter().write(req.getRequestedSessionId());
                break;
            case "isvalid":
                resp.getWriter().write(req.isRequestedSessionIdValid() + "");
                break;
            case "default":
                resp.getWriter().write(req.getRequestedSessionId());
                break;
        }

    }
}
