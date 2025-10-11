/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.errorpage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class ServletErrorDispatchTestCase {


    @BeforeClass
    public static void setup() throws ServletException {


        final ServletContainer container = ServletContainer.Factory.newInstance();
        final PathHandler root = new PathHandler();
        DefaultServer.setRootHandler(root);

        DeploymentInfo builder = new DeploymentInfo();
        builder.addServlet(new ServletInfo("*.jsp", MimicServlet.class)
                .addMapping("*.jsp"));
        builder.addServlet(new ServletInfo("error", GR8MimicServlet.class)
                .addMapping("/bestErrorPageEver"));

        builder.addErrorPage(new ErrorPage("/bestErrorPageEver", StatusCodes.METHOD_NOT_ALLOWED));

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
        .setClassLoader(ServletErrorDispatchTestCase.class.getClassLoader())
        .setContextPath("/servletContext")
        .setDeploymentName("servletContext1.war")
        .setSendCustomReasonPhraseOnError(true);

        final DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
    }

    @Test
    public void testSimpleHttpServlet() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase base = new HttpRequestBase() {

                @Override
                public String getMethod() {
                    return "CONNECT";
                }
            };
            base.setURI(new URI(DefaultServer.getDefaultServerURL() + "/servletContext/bob.jsp"));
            HttpResponse result = client.execute(base);
            Assert.assertEquals(StatusCodes.METHOD_NOT_ALLOWED, result.getStatusLine().getStatusCode());
            Assert.assertEquals(GR8MimicServlet.FAIL_IN_CORRECT_WAY, result.getStatusLine().getReasonPhrase());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    //NOTE: this is bad, tests depend on different handlers/servlets from undertow or jakarta, which have different behavior
    public static class MimicServlet extends DefaultServlet{

        public static final String FAIL_IN_CORRECT_WAY = "Typhon cacoplasmus";
        public static final int FAIL_CODE_IN_A_GOOD_WAY = StatusCodes.METHOD_NOT_ALLOWED;
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if(req.getMethod().equals("CONNECT")) {
                resp.sendError(FAIL_CODE_IN_A_GOOD_WAY, FAIL_IN_CORRECT_WAY);
            } else {
                super.service(req, resp);
            }
        }
    }

    public static class GR8MimicServlet extends DefaultServlet{

        public static final String FAIL_IN_CORRECT_WAY = "Typhon cacoplasmus grandiose";
        public static final int FAIL_CODE_IN_A_GOOD_WAY = StatusCodes.METHOD_NOT_ALLOWED;
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.sendError(FAIL_CODE_IN_A_GOOD_WAY, FAIL_IN_CORRECT_WAY);
        }

    }
}
