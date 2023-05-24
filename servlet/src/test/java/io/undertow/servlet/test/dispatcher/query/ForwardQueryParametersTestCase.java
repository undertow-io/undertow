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
package io.undertow.servlet.test.dispatcher.query;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
public class ForwardQueryParametersTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        // we don't run this test on h2 upgrade, as if it is run with the original request
        // the protocols will not match
        Assume.assumeFalse(DefaultServer.isH2upgrade());
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo().setClassLoader(ForwardQueryParametersTestCase.class.getClassLoader())
                .setContextPath("/servletContext").setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(ForwardQueryParametersTestCase.class))
                .addServlet(new ServletInfo("initial", ForwardingServlet.class)
                        .addInitParam(ForwardingServlet.PARAM_NAME, "initial")
                        .addInitParam(ForwardingServlet.PARAM_VALUE, "was-here")
                        .addInitParam(ForwardingServlet.FWD_TARGET, "/fwd1")
                        .addMapping("/initial"))
                .addServlet(
                        new ServletInfo("fwd1", ForwardingServlet.class).addInitParam(ForwardingServlet.PARAM_NAME, "second")
                                .addInitParam(ForwardingServlet.PARAM_VALUE, "wasnt-here")
                                .addInitParam(ForwardingServlet.FWD_TARGET, "/fwd2")
                                .addMapping("/fwd1"))
                .addServlet(new ServletInfo("fwd2", ForwardingServlet.class)
                        .addMapping("/fwd2"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testPathBasedInclude() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/initial");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals("initial=was-here&second=wasnt-here", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
