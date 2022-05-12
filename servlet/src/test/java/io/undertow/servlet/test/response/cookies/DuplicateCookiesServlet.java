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
 * Servlet that adds duplicate cookies (i.e., with the same name) to response.
 *
 * @author Flavia Rainone
 */
public class DuplicateCookiesServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Cookie cookie1 = new Cookie("test1", "test1");
        cookie1.setPath("/test1_1");

        Cookie cookie2 = new Cookie("test1", "test1");
        cookie2.setPath("/test1_2");

        Cookie cookie3 = new Cookie("test2", "test2");
        cookie3.setPath("/test2");

        Cookie cookie4 = new Cookie("test2", "test2");
        cookie4.setPath("/test2");
        cookie4.setDomain("www.domain2.com");

        Cookie cookie5 = new Cookie("test3", "test3");
        cookie5.setDomain("www.domain3-1.com");

        Cookie cookie6 = new Cookie("test3", "test3");
        cookie6.setDomain("www.domain3-2.com");

        Cookie cookie7 = new Cookie("test3", "test3");


        resp.addCookie(cookie1);
        resp.addCookie(cookie2);
        resp.addCookie(cookie3);
        resp.addCookie(cookie4);
        resp.addCookie(cookie5);
        resp.addCookie(cookie6);
        resp.addCookie(cookie7);

        resp.getWriter().append("Served at: ").append(req.getContextPath());
    }
}
