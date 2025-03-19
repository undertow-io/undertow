/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.dispatcher.attributes;

import java.io.IOException;
import java.util.Enumeration;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author baranowb
 */
public class ErrorHandlingServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        //test if on error we get proper set of attribs, regardless of downstream stuff.
        final Enumeration<String> attribs = req.getAttributeNames();
        final StringBuilder responseBuilder = new StringBuilder();
        while(attribs.hasMoreElements()) {
            final String key = attribs.nextElement();
            final Object value = req.getAttribute(key);
            responseBuilder.append(key);
            responseBuilder.append(";");
            if(value instanceof HttpServletMapping) {
                final HttpServletMapping hsm = (HttpServletMapping) value;
                responseBuilder.append("match_value").append("=").append(hsm.getMatchValue()).append(",");
                responseBuilder.append("pattern").append("=").append(hsm.getPattern()).append(",");
                responseBuilder.append("servlet_name").append("=").append(hsm.getServletName()).append(",");
                responseBuilder.append("mapping_match").append("=").append(hsm.getMappingMatch());
            } else {
                responseBuilder.append(value);
            }
            responseBuilder.append("\n");
        }
        final Enumeration<String> parameters = req.getParameterNames();

        while(parameters.hasMoreElements()) {
            final String key = parameters.nextElement();
            final String value = req.getParameter(key);
            responseBuilder.append(key);
            responseBuilder.append(":");
            responseBuilder.append(value);
            responseBuilder.append("\n");
        }
        resp.getOutputStream().write(responseBuilder.toString().getBytes());
        resp.setStatus(200);
        resp.flushBuffer();
    }

}
