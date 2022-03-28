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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        Cookie cookie1 = new jakarta.servlet.http.Cookie("test", "test1");
        cookie1.setPath("/test");

        Cookie cookie2 = new jakarta.servlet.http.Cookie("test", "test2");
        cookie2.setPath("/test");

        Cookie cookie3 = new jakarta.servlet.http.Cookie("test", "test3");
        Cookie cookie4 = new jakarta.servlet.http.Cookie("test", "test4");
        Cookie cookie5 = new jakarta.servlet.http.Cookie("test", "test5");

        Cookie cookie6 = new jakarta.servlet.http.Cookie("test", "test6");
        cookie6.setPath("/test");
        cookie6.setDomain("www.domain.com");

        Cookie cookie7 = new jakarta.servlet.http.Cookie("test", "test7");
        cookie7.setPath("/test");
        cookie7.setDomain("www.domain.com");

        Cookie cookie8 = new jakarta.servlet.http.Cookie("test", "test8");
        cookie8.setPath("/test");
        cookie8.setDomain("www.domain.com");

        Cookie cookie9 = new jakarta.servlet.http.Cookie("test", "test9");
        cookie9.setDomain("www.domain.com");

        Cookie cookie10 = new jakarta.servlet.http.Cookie("test", "test10");
        cookie10.setDomain("www.domain.com");

        resp.addCookie(cookie1);
        resp.addCookie(cookie2);
        resp.addCookie(cookie3);
        resp.addCookie(cookie4);
        resp.addCookie(cookie5);
        resp.addCookie(cookie6);
        resp.addCookie(cookie7);
        resp.addCookie(cookie8);
        resp.addCookie(cookie9);
        resp.addCookie(cookie10);

        // creating session -> additional jsessionid set-cookie
        req.getSession().setAttribute("CleanSessions", true);

        resp.getWriter().append("Served at: ").append(req.getContextPath());
    }
}
