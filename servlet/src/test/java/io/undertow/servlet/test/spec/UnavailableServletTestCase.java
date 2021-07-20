package io.undertow.servlet.test.spec;

import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;

import java.io.IOException;

import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class UnavailableServletTestCase {


    @Before
    public void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                servlet("p", UnavailableServlet.class)
                        .addInitParam(UnavailableServlet.PERMANENT, "1")
                        .addMapping("/p"),
                servlet("t", UnavailableServlet.class)
                        .addMapping("/t"));

    }

    @After
    public void teardown() {
        UnavailableServlet.reset();
    }

    @Test
    public void testPermanentUnavailableServlet() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/p");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testTempUnavailableServlet() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/t");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.SERVICE_UNAVAILABLE, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Thread.sleep(1001);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/t");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
