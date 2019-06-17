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

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that adds multiple cookies with same name and a few of which sharing
 * the same path, to test cookies with same path and name being correctly
 * overriden.
 *
 * @author Flavia Rainone
 */
public class OverwriteCookiesServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Cookie cookie1 = new javax.servlet.http.Cookie("test", "test1");
        cookie1.setPath("/test");

        Cookie cookie2 = new javax.servlet.http.Cookie("test", "test2");
        cookie2.setPath("/test");

        Cookie cookie3 = new javax.servlet.http.Cookie("test", "test3");
        Cookie cookie4 = new javax.servlet.http.Cookie("test", "test4");
        Cookie cookie5 = new javax.servlet.http.Cookie("test", "test5");

        resp.addCookie(cookie1);
        resp.addCookie(cookie2);
        resp.addCookie(cookie3);
        resp.addCookie(cookie4);
        resp.addCookie(cookie5);

        // creating session -> additional jsessionid set-cookie
        req.getSession().setAttribute("CleanSessions", true);

        resp.getWriter().append("Served at: ").append(req.getContextPath());
    }
}
