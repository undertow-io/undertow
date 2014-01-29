package io.undertow.servlet.test.metrics;

import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

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
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletMetricsHandlerTestCase {

    private static TestMetricsCollector metricsCollector = new TestMetricsCollector();

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DefaultServletTestCase.class));

        builder.addServlet(new ServletInfo("DefaultTestServlet", PathTestServlet.class)
                .addMapping("/path/default"));

        builder.addFilter(new FilterInfo("Filter", HelloFilter.class));
        builder.addFilterUrlMapping("Filter", "/filterpath/*", DispatcherType.REQUEST);
        builder.registerMetricsCollector(metricsCollector);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testMetrics() throws IOException, InterruptedException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path/default");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).contains("pathInfo"));

            MetricsHandler.MetricResult metrics = metricsCollector.getMetrics("DefaultTestServlet");
            Assert.assertEquals(1, metrics.getTotalRequests());
            Assert.assertTrue(metrics.getMaxRequestTime() > 0);
            Assert.assertEquals(metrics.getMinRequestTime(), metrics.getMaxRequestTime());
            Assert.assertEquals(metrics.getMaxRequestTime(), metrics.getTotalRequestTime());


            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertTrue(HttpClientUtils.readResponse(result).contains("pathInfo"));


            metrics = metricsCollector.getMetrics("DefaultTestServlet");
            Assert.assertEquals(2, metrics.getTotalRequests());

        } finally {

            client.getConnectionManager().shutdown();
        }
    }
}
