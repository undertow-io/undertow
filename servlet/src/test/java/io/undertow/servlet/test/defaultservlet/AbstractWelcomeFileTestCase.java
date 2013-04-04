package io.undertow.servlet.test.defaultservlet;

import java.io.IOException;

import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractWelcomeFileTestCase {


    @Test
    public void testWelcomeFileRedirect() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
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
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path?a=b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:a=b servletPath:/path/default requestUri:/servletContext/path/default", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
