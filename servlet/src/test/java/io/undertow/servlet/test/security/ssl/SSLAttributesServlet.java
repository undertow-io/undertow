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

package io.undertow.servlet.test.security.ssl;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HttpConstraint;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
@ServletSecurity(value = @HttpConstraint(

))
public class SSLAttributesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter pw = resp.getWriter();
        if (req.getServletPath().equals("/id")) {
            pw.write(req.getAttribute("jakarta.servlet.request.ssl_session_id").toString());
        } else if (req.getServletPath().equals("/key-size")) {
            pw.write(req.getAttribute("jakarta.servlet.request.key_size").toString());
        } else if (req.getServletPath().equals("/cipher-suite")) {
            pw.write(req.getAttribute("jakarta.servlet.request.cipher_suite").toString());
        } else if (req.getServletPath().equals("/cert")) {
            final X509Certificate[] attribute = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
            if (attribute!=null){
                pw.write(attribute[0].getSerialNumber().toString());
            }
        } else if (req.getServletPath().equals("/cert-dn")) {
            final X509Certificate[] attribute = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
            pw.write(attribute != null && attribute.length > 0? attribute[0].getSubjectDN().toString() : "null");
        }
        pw.close();
    }


}
