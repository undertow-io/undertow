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
package io.undertow.servlet.test.security;

import io.undertow.servlet.test.util.MessageServlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * An extension to the MessageServlet that can also perform additional checks related to the authenticated principal.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AuthenticationMessageServlet extends MessageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String expectedMechanism = req.getHeader("ExpectedMechanism");
        if (expectedMechanism == null) {
            throw new IllegalStateException("No ExpectedMechanism received.");
        }
        if (expectedMechanism.equals("None")) {
            if (req.getAuthType() != null) {
                throw new IllegalStateException("Authentication occured when not expected.");
            }
        } else if (expectedMechanism.equals("BASIC")) {
            if (req.getAuthType() != HttpServletRequest.BASIC_AUTH) {
                throw new IllegalStateException("Expected mechanism type not matched.");
            }
        } else {
            throw new IllegalStateException("ExpectedMechanism not recognised.");
        }

        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

}
