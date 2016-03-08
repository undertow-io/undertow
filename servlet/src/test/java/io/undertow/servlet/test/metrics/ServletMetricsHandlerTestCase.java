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

package io.undertow.servlet.test.metrics;

import io.undertow.server.handlers.MetricsHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.defaultservlet.DefaultServletTestCase;
import io.undertow.servlet.test.defaultservlet.HelloFilter;
import io.undertow.servlet.test.path.ServletPathMappingTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.DispatcherType;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletMetricsHandlerTestCase {

    @Test
    public void testMetrics() throws Exception {


        final TestMetricsCollector metricsCollector = new TestMetricsCollector();

        CompletionLatchHandler completionLatchHandler;
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DefaultServletTestCase.class));

        builder.addServlet(new ServletInfo("MetricTestServlet", MetricTestServlet.class)
                .addMapping("/path/default"));

        builder.addFilter(new FilterInfo("Filter", HelloFilter.class));
        builder.addFilterUrlMapping("Filter", "/filterpath/*", DispatcherType.REQUEST);
        builder.setMetricsCollector(metricsCollector);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(completionLatchHandler = new CompletionLatchHandler(root));

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path/default");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).contains("metric"));
            completionLatchHandler.await();
            completionLatchHandler.reset();

            MetricsHandler.MetricResult metrics = metricsCollector.getMetrics("MetricTestServlet");
            Assert.assertEquals(1, metrics.getTotalRequests());
            Assert.assertTrue(metrics.getMaxRequestTime() > 0);
            Assert.assertEquals(metrics.getMinRequestTime(), metrics.getMaxRequestTime());
            Assert.assertEquals(metrics.getMaxRequestTime(), metrics.getTotalRequestTime());


            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).contains("metric"));
            completionLatchHandler.await();
            completionLatchHandler.reset();


            metrics = metricsCollector.getMetrics("MetricTestServlet");
            Assert.assertEquals(2, metrics.getTotalRequests());

        } finally {

            client.getConnectionManager().shutdown();
        }
    }
}
