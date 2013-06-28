package io.undertow.server.handlers.accesslog;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class AccessLogTestCase {

    private static volatile String message;

    private volatile CountDownLatch latch;


    private final AccessLogReceiver RECIEVER = new AccessLogReceiver() {


        @Override
        public void logMessage(final String msg) {
            message = msg;
            latch.countDown();
        }
    };

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send("Hello");
        }
    };

    @Test
    public void testRemoteAddress() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        DefaultServer.setRootHandler(new AccessLogHandler(HELLO_HANDLER, RECIEVER, "Remote address %a Code %s test-header %{test-header}i", AccessLogFileTestCase.class.getClassLoader()));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "test-value");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latch.await(10, TimeUnit.SECONDS);
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header test-value", message);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
