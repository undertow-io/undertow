package io.undertow.server.handlers.jdbclog;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class JDBCLogTestCase {
    private static volatile String message;

    private volatile CountDownLatch latch;

    private final JDBCLogReceiver RECEIVER = new JDBCLogReceiver() {
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
        DefaultServer.setRootHandler(new JDBCLogHandler(HELLO_HANDLER, RECEIVER, "Remote address %a Code %s user-agent %{i,user-agent}", JDBCLogTestCase.class.getClassLoader()));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("user-agent", "Test/1.0.0");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latch.await(10, TimeUnit.SECONDS);
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 user-agent Test/1.0.0", message);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
