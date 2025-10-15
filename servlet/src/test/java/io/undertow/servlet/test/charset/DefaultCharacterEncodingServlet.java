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

package io.undertow.servlet.test.charset;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * @author Artemy Osipov
 */
public class DefaultCharacterEncodingServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String requestCharacterEncoding = req.getCharacterEncoding();
        String responseCharacterEncoding = resp.getCharacterEncoding();

        PrintWriter writer = resp.getWriter();
        writer.write(String.format("requestCharacterEncoding=%s;responseCharacterEncoding=%s;",
                requestCharacterEncoding, responseCharacterEncoding));
        writer.close();
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final Reader reader = req.getReader();
        final char[] buf = new char[1024];
        final StringBuilder contentBuilder = new StringBuilder();
        int numRead = -1;
        while ((numRead = reader.read(buf)) != -1) {
            contentBuilder.append(buf, 0, numRead);
        }
        final String requestCharacterEncoding = req.getCharacterEncoding();
        final String responseCharacterEncoding = resp.getCharacterEncoding();

        final PrintWriter writer = resp.getWriter();
        writer.write(String.format("requestCharacterEncoding=%s;responseCharacterEncoding=%s;content=%s;",
                requestCharacterEncoding, responseCharacterEncoding, contentBuilder.toString()));
        writer.close();
    }
}
