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

package io.undertow.test.jsp.basic;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;

import io.undertow.jsp.HackInstanceManager;
import io.undertow.jsp.JspServletBuilder;
import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.jasper.deploy.JspPropertyGroup;
import org.apache.jasper.deploy.TagLibraryInfo;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleJspTestCase {

    public static final String KEY = "io.undertow.message";

    @BeforeClass
    public static void setup() throws ServletException {

        final CookieHandler cookieHandler = new CookieHandler();
        final SessionAttachmentHandler session = new SessionAttachmentHandler(new InMemorySessionManager());
        cookieHandler.setNext(session);
        final PathHandler servletPath = new PathHandler();
        session.setNext(servletPath);
        final ServletContainer container = ServletContainer.Factory.newInstance();



        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleJspTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(new TestResourceLoader(SimpleJspTestCase.class))
                .addServlet(JspServletBuilder.createServlet("Default Jsp Servlet", "*.jsp"));
        JspServletBuilder.setupDeployment(builder, new HashMap<String, JspPropertyGroup>(), new HashMap<String, TagLibraryInfo>(), new HackInstanceManager());

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        servletPath.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(cookieHandler);
        System.setProperty(KEY, "Hello JSP!");
    }

    @AfterClass
    public static void after(){
        System.getProperties().remove(KEY);
    }

    @Test
    public void testSimpleHttpServlet() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a.jsp");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("<HTML><BODY> Message:Hello JSP!</BODY></HTML>", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
