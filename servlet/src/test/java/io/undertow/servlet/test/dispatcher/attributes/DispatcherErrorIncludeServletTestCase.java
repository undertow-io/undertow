/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.dispatcher.attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.ServletException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class DispatcherErrorIncludeServletTestCase extends AttributeComparisonTestBase {

    static final String TARGET = "/target";
    @BeforeClass
    public static void setup() throws ServletException {
        //we don't run this test on h2 upgrade, as if it is run with the original request
        //the protocols will not match
        Assume.assumeFalse(DefaultServer.isH2upgrade());
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                //no idea why...
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DispatcherErrorIncludeServletTestCase.class))
                //test servlet, we need info from it
                .addServlet(new ServletInfo("error", ErrorHandlingServlet.class)
                        .addMapping("/error"))
                //return handler, which should send us stuff
                .addServlet(new ServletInfo("target", ErrorSpewServlet.class)
                .addMapping(TARGET))
                //fwd
                .addServlet(new ServletInfo("inc", IncludeServlet.class)
                .addMapping("/include"))
                //error mapge mapping to servlet
                .addErrorPage(new ErrorPage("/error"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testSimpleIncludeWithError() throws IOException, InterruptedException {
        final Map<String, String> expectedParams = new TreeMap();
        //        javax.servlet.async.mapping
        //        javax.servlet.async.request_uri
        //        javax.servlet.async.context_path
        //        javax.servlet.async.servlet_path
        //        javax.servlet.async.path_info
        //        javax.servlet.async.query_string

        expectedParams.put("javax.servlet.forward.request_uri", "/servletContext/include");
        expectedParams.put("javax.servlet.forward.servlet_path", "/include");
        expectedParams.put("javax.servlet.error.servlet_name", "inc");
        expectedParams.put("javax.servlet.forward.mapping",
                "match_value=include,pattern=/include,servlet_name=inc,mapping_match=EXACT");
        expectedParams.put("javax.servlet.forward.context_path", "/servletContext");
        //https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf chapter 10.9.1
        //        javax.servlet.error.request_uri
        //        javax.servlet.error.servlet_name
        //        javax.servlet.error.exception_type
        //        javax.servlet.error.exception
        //        javax.servlet.error.message
        //        javax.servlet.error.status_code
        expectedParams.put("javax.servlet.error.request_uri", "/servletContext/include");
        expectedParams.put("javax.servlet.error.exception_type", "class javax.servlet.ServletException");
        expectedParams.put("javax.servlet.error.exception", "javax.servlet.ServletException: HEY");
        expectedParams.put("javax.servlet.error.message", "HEY");
        expectedParams.put("javax.servlet.error.status_code", "500");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/include");
            get.setHeader("include", TARGET);
            get.setHeader("throw", "true");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertNotNull(response);
            super.testAttributes(expectedParams, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSimpleIncludeWithNoError() throws IOException, InterruptedException {
        //https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf chapter 9.4.2
        //        javax.servlet.include.request_uri
        //        javax.servlet.include.context_path
        //        javax.servlet.include.query_string
        //        javax.servlet.include.servlet_path
        //        javax.servlet.include.mapping
        //        javax.servlet.include.path_info
        final Map<String, String> expectedParams = new TreeMap();
        expectedParams.put("javax.servlet.include.request_uri", "/servletContext/target");
        expectedParams.put("javax.servlet.include.context_path", "/servletContext");
        expectedParams.put("javax.servlet.include.query_string", "");
        expectedParams.put("javax.servlet.include.servlet_path", TARGET);
        expectedParams.put("javax.servlet.include.mapping",
                "match_value=include,pattern=/include,servlet_name=inc,mapping_match=EXACT");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/include");
            get.setHeader("include", TARGET);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertNotNull(response);
            super.testAttributes(expectedParams, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
