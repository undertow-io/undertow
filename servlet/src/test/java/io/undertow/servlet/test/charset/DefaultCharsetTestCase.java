package io.undertow.servlet.test.charset;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.spec.ServletContextImpl;
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
public class DefaultCharsetTestCase {


    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContextImpl servletContext) {
                deploymentInfo.setDefaultEncoding("UTF-8");
            }
        }, new ServletInfo("servlet", DefaultCharsetServlet.class)
                        .addMapping("/"));
    }

    public static byte[] toByteArray(int[] source) {
        byte[] ret = new byte[source.length];
        for (int i = 0; i < source.length; ++i) {
            ret[i] = (byte) (0xff & source[i]);
        }
        return ret;
    }

    private static final byte[] UTF8 = toByteArray(new int[]{0x41, 0xC2, 0xA9, 0xC3, 0xA9, 0xCC, 0x81, 0xE0, 0xA5, 0x81, 0xF0, 0x9D, 0x94, 0x8A});

    @Test
    public void testCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            byte[] response = HttpClientUtils.readRawResponse(result);
            Assert.assertArrayEquals(UTF8, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
