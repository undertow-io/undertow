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

package io.undertow.servlet.test.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Istvan Szabo
 */
public class ParameterEchoServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String echoType = req.getParameter("type");
        if (echoType == null) echoType = "values";
        StringBuilder sb = new StringBuilder();
        if (echoType.equals("values")) {
            sb = echoParameterValues(req);
        } else if (echoType.equals("names")) {
            sb = echoParameterNames(req);
        } else if (echoType.equals("map")) {
            sb = echoParameterMap(req);
        } else {
            resp.sendError(400);
            return;
        }

        PrintWriter writer = resp.getWriter();
        writer.write(sb.toString());
        writer.close();
    }

    private StringBuilder echoParameterMap(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        Map<String, String[]> map = req.getParameterMap();
        for (Map.Entry<String, String[]> entry: map.entrySet()) {
            sb.append(entry.getKey()).append("=");
            for (int i = 0; i < entry.getValue().length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(entry.getValue()[i]);
            }
            sb.append(";");
        }
        return sb;
    }

    private StringBuilder echoParameterNames(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            sb.append(names.nextElement());
            if (names.hasMoreElements()) sb.append(",");
        }
        return sb;
    }

    private StringBuilder echoParameterValues(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        String[] param1Values = req.getParameterValues("param1");
        String[] param2Values = req.getParameterValues("param2");
        String[] param3Values = req.getParameterValues("param3");

        if (param1Values != null) {
            sb.append("param1=\'");
            for (int i = 0; i < param1Values.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(param1Values[i]);
            }
            sb.append('\'');
        }
        if (param2Values != null) {
            sb.append("param2=\'");
            for (int i = 0; i < param2Values.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(param2Values[i]);
            }
            sb.append('\'');
        }
        if (param3Values != null) {
            sb.append("param3=\'");
            for (int i = 0; i < param3Values.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(param3Values[i]);
            }
            sb.append('\'');
        }
        return sb;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }

}
