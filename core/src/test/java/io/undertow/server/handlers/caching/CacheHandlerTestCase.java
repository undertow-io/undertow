package io.undertow.server.handlers.caching;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests out the caching handler
 *
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CacheHandlerTestCase {


    private static final AtomicInteger responseCount = new AtomicInteger();

    @BeforeClass
    public static void setup() {

        final HttpHandler messageHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
                if(!cache.tryServeResponse()) {
                    final String data = "Response " + responseCount.incrementAndGet();
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, data.length() + "");
                    exchange.getResponseSender().send(data);
                }
            }
        };
        final CacheHandler cacheHandler = new CacheHandler(new DirectBufferCache(100, 10, 1000), messageHandler);
        DefaultServer.setRootHandler(cacheHandler);
    }

    @Test
    public void testBasicPathBasedCaching() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            //it takes 5 hits to make an entry actually get cached
            for (int i = 1; i <= 5; ++i) {
                HttpResponse result = client.execute(get);
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                Assert.assertEquals("Response " + i, HttpClientUtils.readResponse(result));
            }

            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path2");

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 6", HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
