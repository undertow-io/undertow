/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.response.cookies;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that emulates a buggy behavior where JSessionID cookie is added several times with
 * wrong path, and a few of them with max age limits for cookie expiration.
 *
 * @author Flavia Rainone
 */
public class JSessionIDCookiesServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        jakarta.servlet.http.Cookie cookie1 = new jakarta.servlet.http.Cookie("JSESSIONID", "_bug_fix");
        cookie1.setPath("/path1");
        cookie1.setMaxAge(0);

        jakarta.servlet.http.Cookie cookie2 = new jakarta.servlet.http.Cookie("JSESSIONID", "_bug_fix");
        cookie2.setPath("/path2");
        cookie2.setMaxAge(0);

        jakarta.servlet.http.Cookie cookie3 = new jakarta.servlet.http.Cookie("JSESSIONID", "_bug_fix");
        cookie1.setPath("/path3");
        cookie1.setMaxAge(500);

        jakarta.servlet.http.Cookie cookie4 = new jakarta.servlet.http.Cookie("JSESSIONID", "_bug_fix");
        cookie2.setPath("/path4");
        cookie2.setMaxAge(1000);

        resp.addCookie(cookie1);
        resp.addCookie(cookie2);
        resp.addCookie(cookie3);
        resp.addCookie(cookie4);

        // creating session -> additional set-cookie
        req.getSession().setAttribute("CleanSessions", true);

        resp.getWriter().append("Served at: ").append(req.getContextPath());
    }
}
