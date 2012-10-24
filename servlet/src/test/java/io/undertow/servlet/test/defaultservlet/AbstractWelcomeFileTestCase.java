package io.undertow.servlet.test.defaultservlet;

import java.io.IOException;

import io.undertow.servlet.test.runner.HttpClientUtils;
import io.undertow.servlet.test.runner.ServletServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class AbstractWelcomeFileTestCase {


    @Test
    public void testWelcomeFileRedirect() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(ServletServer.getDefaultServerAddress() + "/servletContext/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("Redirected home page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testWelcomeServletRedirect() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(ServletServer.getDefaultServerAddress() + "/servletContext/path?a=b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:a=b servletPath:/path/default requestUri:/servletContext/path/default", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
