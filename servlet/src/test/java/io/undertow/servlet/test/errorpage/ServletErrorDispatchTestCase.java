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
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class ServletErrorDispatchTestCase {

    private static final String CONNECT = "CONNECT";
    private static final String PROTOCOL = "HTTP/1.1";

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
    public void testSimpleHttpServlet() throws IOException {
        // testing with Socket because HttpClient doesn't allow invoking CONNECT
        try (Socket socket = new Socket()) {
            socket.connect(DefaultServer.getDefaultServerAddress());
            try (OutputStream outputStream = socket.getOutputStream();
                    InputStream input = socket.getInputStream()) {
                String request = CONNECT + " /servletContext/bob.jsp " + PROTOCOL + "\r\nHost:localhost\r\n\r\n";

                outputStream.write(request.getBytes(StandardCharsets.US_ASCII));
                BufferedReader br = new BufferedReader(new InputStreamReader(input));
                String line = br.readLine();
                Assert.assertEquals(PROTOCOL + " " + StatusCodes.METHOD_NOT_ALLOWED + " " + GR8MimicServlet.FAIL_IN_CORRECT_WAY, line);
            }
        }
    }

    //NOTE: this is bad, tests depend on different handlers/servlets from undertow or jakarta, which have different behavior
    public static class MimicServlet extends DefaultServlet {

        public static final String FAIL_IN_CORRECT_WAY = "Typhon cacoplasmus";
        public static final int FAIL_CODE_IN_A_GOOD_WAY = StatusCodes.METHOD_NOT_ALLOWED;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (req.getMethod().equals(CONNECT)) {
                resp.sendError(FAIL_CODE_IN_A_GOOD_WAY, FAIL_IN_CORRECT_WAY);
            } else {
                super.service(req, resp);
            }
        }
    }

    public static class GR8MimicServlet extends DefaultServlet {

        public static final String FAIL_IN_CORRECT_WAY = "Typhon cacoplasmus grandiose";
        public static final int FAIL_CODE_IN_A_GOOD_WAY = StatusCodes.METHOD_NOT_ALLOWED;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.sendError(FAIL_CODE_IN_A_GOOD_WAY, FAIL_IN_CORRECT_WAY);
        }

    }
}
