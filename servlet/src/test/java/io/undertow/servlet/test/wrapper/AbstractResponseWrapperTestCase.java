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

package io.undertow.servlet.test.wrapper;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.LoggingExceptionHandler;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests wrapped requests and responses
 *
 * TODO: these tests should be expanded to add more functionality to the wrappers, and also test request dispatches
 *
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public abstract class AbstractResponseWrapperTestCase {


    @Before
    public void setup() throws ServletException {
        DeploymentInfo builder = new DeploymentInfo();
        builder.setExceptionHandler(LoggingExceptionHandler.builder().add(IllegalArgumentException.class, "io.undertow", Logger.Level.DEBUG).build());

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        builder.addServlet(new ServletInfo("wrapperServlet", WrapperServlet.class)
                .addMapping("/*"));


        builder.addFilter(new FilterInfo("standard", StandardRequestWrappingFilter.class));
        builder.addFilterUrlMapping("standard", "/standard", DispatcherType.REQUEST);

        builder.addFilter(new FilterInfo("nonstandard", NonStandardRequestWrappingFilter.class));
        builder.addFilterUrlMapping("nonstandard", "/nonstandard", DispatcherType.REQUEST);

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(AbstractResponseWrapperTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setAllowNonStandardWrappers(isNonStandardAllowed());

        final DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    abstract boolean isNonStandardAllowed();

    @Test
    public void testNoWrapper() throws IOException, ServletException {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HttpServletRequestImpl.class.getName() + "\n" + HttpServletResponseImpl.class.getName(), response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testStandardWrapper() throws IOException, ServletException {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/standard");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(StandardRequestWrapper.class.getName() + "\n" + StandardResponseWrapper.class.getName(), response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
