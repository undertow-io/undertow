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
package io.undertow.servlet.test.security.constraint;

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
        checkExpectedMechanism(req);
        checkExpectedUser(req);

        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    private void checkExpectedMechanism(HttpServletRequest req) {
        String expectedMechanism = req.getHeader("ExpectedMechanism");
        if (expectedMechanism == null) {
            throw new IllegalStateException("No ExpectedMechanism received.");
        }
        if (expectedMechanism.equals("None")) {
            if (req.getAuthType() != null) {
                throw new IllegalStateException("Authentication occurred when not expected.");
            }
        } else if (expectedMechanism.equals("BASIC")) {
            if (req.getAuthType() != HttpServletRequest.BASIC_AUTH) {
                throw new IllegalStateException("Expected mechanism type not matched: " + req.getAuthType());
            }
        } else {
            throw new IllegalStateException("ExpectedMechanism not recognised.");
        }
    }

    private void checkExpectedUser(HttpServletRequest req) {
        String expectedUser = req.getHeader("ExpectedUser");
        if (expectedUser == null) {
            throw new IllegalStateException("No ExpectedUser received.");
        }
        if (expectedUser.equals("None")) {
            if (req.getRemoteUser() != null) {
                throw new IllegalStateException("Unexpected RemoteUser returned.");
            }
            if (req.getUserPrincipal() != null) {
                throw new IllegalStateException("Unexpected UserPrincipal returned.");
            }
        } else {
            if (req.getRemoteUser().equals(expectedUser) == false) {
                throw new IllegalStateException("Different RemoteUser returned.");
            }
            if (req.getUserPrincipal().getName().equals(expectedUser) == false) {
                throw new IllegalStateException("Different UserPrincipal returned.");
            }
        }
    }

}
