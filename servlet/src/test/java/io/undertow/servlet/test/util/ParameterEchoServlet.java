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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Istvan Szabo
 */
public class ParameterEchoServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();
        String[] param1Values = req.getParameterValues("param1");
        String[] param2Values = req.getParameterValues("param2");
        String[] param3Values = req.getParameterValues("param3");
        StringBuilder sb = new StringBuilder();
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
        writer.write(sb.toString());
        writer.close();
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
