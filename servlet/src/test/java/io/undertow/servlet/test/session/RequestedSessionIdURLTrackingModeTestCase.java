/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.session;

import java.io.IOException;
import java.util.Collections;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * Testing getRequestedSessionId when is null and when client specifies a sessionId
 *
 * @author tmiyar
 */
@RunWith(DefaultServer.class)
public class RequestedSessionIdURLTrackingModeTestCase {


    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                deploymentInfo.setServletSessionConfig(new ServletSessionConfig().setSessionTrackingModes(Collections.singleton(SessionTrackingMode.URL)));
            }
        }, Servlets.servlet(RequestedSessionIdServlet.class).addMapping("/test"));
    }



    @Test
    public void testGetRequestedSessionId() throws IOException {
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/test;jsessionid=null");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/test;jsessionid=test");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.close();
        }
    }


    /**
     * The SessionManager.createSession(true) *MUST* call {@link SessionConfig#findSessionId(io.undertow.server.HttpServerExchange)} (io.undertow.server.HttpServerExchange)} first to
     * determine if an existing session ID is present in the exchange. If this id is present then it must be used
     * as the new session ID.
     * @author tmiyar
     * @see io.undertow.server.session.SessionManager
     */
    public static class RequestedSessionIdServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            //Before there is any session
            String sessionIdBefore = req.getRequestedSessionId();
            //create a new session
            req.getSession(true);
            //should return client provided session
            String sessionIdAfter = req.getRequestedSessionId();

            Assert.assertTrue(String.format("sessionIdBefore %s, sessionIdAfter %s", sessionIdBefore, sessionIdAfter), sessionIdBefore.equals(sessionIdAfter));

        }
    }

}
