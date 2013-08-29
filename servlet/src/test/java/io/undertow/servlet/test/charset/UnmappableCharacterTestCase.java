package io.undertow.servlet.test.charset;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class UnmappableCharacterTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(Servlets.servlet("servlet", EchoServlet.class)
                .addMapping("/"));
    }


    @Test
    public void testUnmappableCharacters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšžgg";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext?message=" + message);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("abc???gg", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
