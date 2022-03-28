/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.response.writer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Stuart Douglas
 */
public class ResponseWriterServlet extends HttpServlet {

    public static final String CONTENT_LENGTH_FLUSH = "content-length-flush";

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        String test = req.getParameter("test");
        if (test.equals(CONTENT_LENGTH_FLUSH)) {
            contentLengthFlush(req, resp);
        } else {
            throw new IllegalArgumentException("not a test " + test);
        }
    }

    private void contentLengthFlush(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int size = 10;

        PrintWriter pw = resp.getWriter();
        StringBuffer tmp = new StringBuffer(2 * size);
        int i = 0;

        pw.write("first-");
        resp.setContentLength(size);
        //write more data than the content length
        while (i < 20) {
            tmp = tmp.append("a");
            i = i + 1;
        }
        pw.println(tmp);
        resp.addHeader("not-header", "not");
    }
}
