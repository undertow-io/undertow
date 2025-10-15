/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.push;

import java.io.IOException;
import java.util.Base64;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.PushBuilder;

/**
 * <p>Simple servlet that pushes resources for index.html and *.css. The idea
 * is that double promises are not sent as they are forbidden by the spec.</p>
 *
 * @author rmartinc
 */
public class PushServlet extends HttpServlet {

    // A simple blue pixel in PNG format
    private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getServletPath().endsWith(".html") || request.getPathInfo().endsWith(".html")) {
            PushBuilder pushBuilder = request.newPushBuilder();
            if (pushBuilder != null) {
                // pushing css and js in advance
                pushBuilder.path("resources/one.css").push();
                pushBuilder.path("resources/one.js").push();
            }
            try (ServletOutputStream out = response.getOutputStream()) {
                out.println("<html>");
                out.println("  <head>");
                out.println("    <link type=\"text/css\" rel=\"stylesheet\" href=\"resources/one.css\" />");
                out.println("    <script type=\"text/javascript\" src=\"resources/one.js\"></script>");
                out.println("  </head>");
                out.println("  <body>");
                out.println("    <span class=\"border\">PUSH PROMISES</span>");
                out.println("  </body>");
                out.println("</html>");
            }
        } else if (request.getPathInfo().endsWith(".css")) {
            PushBuilder pushBuilder = request.newPushBuilder();
            if (pushBuilder != null) {
                // pushing images in advance
                pushBuilder.path("resources/one.png").push();
            }
            response.setContentType("text/css");
            try (ServletOutputStream out = response.getOutputStream()) {
                out.println("body, html {");
                out.println("  height: 100%;");
                out.println("  margin: 0;");
                out.println("  background-image: url(\"one.png\");");
                out.println("  background-repeat: repeat;");
                out.println("}");
            }
        } else if (request.getPathInfo().endsWith(".js")) {
            response.setContentType("application/javascript");
            try (ServletOutputStream out = response.getOutputStream()) {
                out.println("console.log('loading js file ' + location.pathname);");
            }
        } else if (request.getPathInfo().endsWith(".png")) {
            byte[] bytes = Base64.getDecoder().decode(PNG_BASE64);
            response.setContentType("image/png");
            try (ServletOutputStream out = response.getOutputStream()) {
                out.write(bytes);
            }
        } else {
            throw new ServletException("Invalid request");
        }
    }
}
