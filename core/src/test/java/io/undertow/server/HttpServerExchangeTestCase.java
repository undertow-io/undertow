package io.undertow.server;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpServerExchangeTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(exchange.getHostName()
                        + ":" + exchange.getProtocol()
                        + ":" + exchange.getRequestMethod()
                        + ":" + exchange.getHostPort()
                        + ":" + exchange.getRequestURI()
                        + ":" + exchange.getRelativePath()
                        + ":" + exchange.getQueryString());
            }
        });
    }


    @Test
    public void testHttpServerExchange() throws IOException {

        final TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("localhost:HTTP/1.1:GET:7777:/somepath:/somepath:", HttpClientUtils.readResponse(result));
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath?a=b");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("localhost:HTTP/1.1:GET:7777:/somepath:/somepath:a=b", HttpClientUtils.readResponse(result));
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath?a=b");
            get.addHeader("Host", "[::1]:8080");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("::1:HTTP/1.1:GET:8080:/somepath:/somepath:a=b", HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
