package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */

@RunWith(DefaultServer.class)
public class VirtualHostTestCase {

    /**
     * Tests the Origin header is respected when the strictest options are selected
     */
    @Test
    public void testVirtualHost() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final NameVirtualHostHandler handler = new NameVirtualHostHandler()
                    .addHost("localhost", new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "localhost"))
                    .setDefaultHandler(new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "default"));


            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders("myHost");
            Assert.assertEquals("localhost", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet("http://" + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + ":" + DefaultServer.getDefaultServerAddress().getPort() + "/path");
            result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders("myHost");
            Assert.assertEquals("default", header[0].getValue());
            HttpClientUtils.readResponse(result);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
