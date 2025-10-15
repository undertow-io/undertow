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

package io.undertow.servlet.test.streams;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class BlockingOutputStreamServlet extends HttpServlet {


    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        boolean flush = req.getParameter("flush") != null;
        boolean close = req.getParameter("close") != null;
        boolean initialFlush = req.getParameter("initialFlush") != null;
        int reps = Integer.parseInt(req.getParameter("reps"));
        ServletOutputStream out = resp.getOutputStream();
        if(initialFlush) {
            resp.flushBuffer();
        }
        for(int i = 0; i < reps; ++i) {
            out.write(ServletOutputStreamTestCase.message.getBytes());
        }
        if(flush) {
            out.flush();
        }
        if(close) {
            out.close();
        }
    }
}
