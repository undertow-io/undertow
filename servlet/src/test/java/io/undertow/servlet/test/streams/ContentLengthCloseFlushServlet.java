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

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class ContentLengthCloseFlushServlet extends HttpServlet {

    private boolean completed = false;

    @Override
    protected synchronized void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (completed) {
            completed = false;
            resp.getWriter().write("OK");
        } else {
            resp.setContentLength(1);
            ServletOutputStream stream = resp.getOutputStream();
            stream.write('a'); //the stream should automatically close here, because it is the content length, but flush should still work
            stream.flush();
            stream.close();
            completed = true;
        }
    }
}
