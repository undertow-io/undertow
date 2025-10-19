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

package io.undertow.servlet.test.multipart;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.TreeSet;

import io.undertow.util.Headers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import io.undertow.util.FileUtils;

/**
 * @author Stuart Douglas
 */
public class MultiPartServlet extends HttpServlet {

    private final boolean getParam;
    public MultiPartServlet() {
        super();
        this.getParam = false;
    }

    public MultiPartServlet(boolean getParam) {
        super();
        this.getParam = getParam;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            Collection<Part> parts = req.getParts();
            resp.setContentType("text/plain; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.println("PARAMS:");
            writer.println("parameter count: " + req.getParameterMap().size());
            writer.println("parameter name count: " + count(req.getParameterNames()));
            for (Part part : parts) {
                writer.println("name: " + part.getName());
                writer.println("filename: " + part.getSubmittedFileName());
                writer.println("content-type: " + part.getContentType());
                Collection<String> headerNames = new TreeSet<>(part.getHeaderNames());
                for (String header : headerNames) {
                    writer.println(header + ": " + part.getHeader(header));
                    if (header.equals(Headers.CONTENT_DISPOSITION_STRING)) {
                        final String parameterValue = part.getHeader(header);
                        String parameterName = parameterValue.substring(parameterValue.indexOf("name=") + "name=\"".length());
                        parameterName = parameterName.substring(0, parameterName.indexOf('\"'));
                        final String[] values = req.getParameterValues(parameterName);
                        if (values != null) {
                            for (String value: values) {
                                writer.println("value: " + value);
                            }
                        }
                    }
                }
                writer.println("size: " + part.getSize());
                writer.println("content: " + FileUtils.readFile(part.getInputStream()));
            }
            if (getParam) {
                Enumeration<String> paramNames = req.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    String name = paramNames.nextElement();
                    writer.println("param name: " + name);
                    writer.println("param value: " + req.getParameter(name));
                }
            }
        } catch (Exception e) {
            resp.getWriter().write("EXCEPTION: " + e.getClass());
        }
    }

    private int count(Enumeration<String> parameterNames) {
        int count = 0;
        while(parameterNames.hasMoreElements()) {
            parameterNames.nextElement();
            count++;
        }
        return count;
    }
}
