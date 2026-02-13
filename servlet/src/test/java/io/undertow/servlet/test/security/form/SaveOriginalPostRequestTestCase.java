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

package io.undertow.servlet.test.security.form;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * <p>Tests if a request made to a secured resource is saved before the client is redirect to the login form. Once the authentication is
 * done, the server should restore the original/saved request.</p>
 *
 * @author Pedro Igor
 */
@RunWith(DefaultServer.class)
public class SaveOriginalPostRequestTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler path = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo securedRequestDumper = new ServletInfo("SecuredRequestDumperServlet", RequestDumper.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/dumpRequest");

        ServletInfo securedIndexRequestDumper = new ServletInfo("SecuredIndexRequestDumperServlet", RequestDumper.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/index.html");
        ServletInfo unsecuredRequestDumper = new ServletInfo("UnsecuredRequestDumperServlet", RequestDumper.class)
                .addMapping("/dumpRequest");
        ServletInfo loginFormServlet = new ServletInfo("loginPage", FormLoginServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("group1"))
                .addMapping("/FormLoginServlet");

        ServletIdentityManager identityManager = new ServletIdentityManager();

        identityManager.addUser("user1", "password1", "role1");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .addWelcomePage("index.html")
                .setResourceManager(new TestResourceLoader(SaveOriginalPostRequestTestCase.class))
                .setLoginConfig(new LoginConfig("FORM", "Test Realm", "/FormLoginServlet", "/error.html"))
                .addServlets(securedRequestDumper, unsecuredRequestDumper, loginFormServlet, securedIndexRequestDumper);

        DeploymentManager manager = container.addDeployment(builder);

        manager.deploy();

        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testParametersFromOriginalPostRequest() throws IOException {
        CloseableHttpClient client = createHttpClient();

        // let's test if a usual POST request have its parameters dumped in the response
        HttpClientResponseHandler<Void> handler = result -> {
            assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);
            assertTrue(response.contains("param1=param1Value"));
            assertTrue(response.contains("param2=param2Value"));
            return null;
        };
        executePostRequest(client, handler, "/servletContext/dumpRequest", new BasicNameValuePair("param1", "param1Value"), new BasicNameValuePair("param2", "param2Value"));

        // this request should be saved and the client redirect to the login form.
        handler = result -> {
            assertEquals(StatusCodes.OK, result.getCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).startsWith("j_security_check"));
            return null;
        };
        executePostRequest(client, handler, "/servletContext/secured/dumpRequest", new BasicNameValuePair("securedParam1", "securedParam1Value"), new BasicNameValuePair("securedParam2", "securedParam2Value"));

        // let's perform a successful authentication and get the request restored
        handler = result -> {
            assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);

            // let's check if the original request was saved, including its parameters.
            assertTrue(response.contains("securedParam1=securedParam1Value"));
            assertTrue(response.contains("securedParam2=securedParam2Value"));
            return null;
        };
        executePostRequest(client, handler, "/servletContext/j_security_check", new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1"));
    }

    @Test
    public void testSavedRequestWithWelcomeFile() throws IOException {
        CloseableHttpClient client = createHttpClient();

        // this request should be saved and the client redirect to the login form.
        HttpClientResponseHandler<Void> handler = result -> {
            assertEquals(StatusCodes.OK, result.getCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).startsWith("j_security_check"));
            return null;
        };
        executePostRequest(client, handler, "/servletContext/", new BasicNameValuePair("securedParam1", "securedParam1Value"), new BasicNameValuePair("securedParam2", "securedParam2Value"));

        // let's perform a successful authentication and get the request restored
        handler = result -> {
            assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);

            // let's check if the original request was saved, including its parameters.
            assertTrue(response.contains("securedParam1=securedParam1Value"));
            assertTrue(response.contains("securedParam2=securedParam2Value"));
            return null;
        };
        executePostRequest(client, handler, "/servletContext/j_security_check", new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1"));
    }

    private CloseableHttpClient createHttpClient() {
        return TestHttpClient.custom()
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                        if (response.getCode() == StatusCodes.FOUND) {
                            return true;
                        }
                        return super.isRedirected(request, response, context);
                    }
                }).build();
    }

    private void executePostRequest(CloseableHttpClient client, HttpClientResponseHandler<Void> handler, String uri, BasicNameValuePair... parameters) throws IOException {
        HttpPost request = new HttpPost(DefaultServer.getDefaultServerURL() + uri);

        request.setEntity(new UrlEncodedFormEntity(new ArrayList<NameValuePair>(Arrays.asList(parameters))));

        client.execute(request, handler);
    }

    static class RequestDumper extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            dumpRequest(req, resp);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            dumpRequest(req, resp);
        }

        private void dumpRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder buffer = new StringBuilder();

            PrintWriter writer = resp.getWriter();

            buffer.append("Method: " + req.getMethod() + "\n");

            Enumeration<String> parameterNames = req.getParameterNames();

            buffer.append("Parameters: ");

            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                buffer.append(parameterName + "=" + req.getParameter(parameterName));
                buffer.append("/");
            }

            writer.write(buffer.toString());
        }
    }
}
