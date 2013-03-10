package io.undertow.test.handlers.caching;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.io.IoCallback;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.CachedHttpRequest;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests out the caching handler when being used with a content encoding
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CacheHandlerContentEncodingTestCase {


    private static final AtomicInteger responseCount = new AtomicInteger();

    /**
     * We use this header to control the predicate that decides when to deflate. Other than this header
     * the requests are identical
     */
    public static final HttpString ACTUALLY_DEFLATE = new HttpString("ActuallyDeflate");

    @BeforeClass
    public static void setup() {

        final HttpHandler messageHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
                if (!cache.tryServeResponse()) {
                    final String data = "Response " + responseCount.incrementAndGet();
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, data.length() + "");
                    exchange.getResponseSender().send(data, IoCallback.END_EXCHANGE);
                }
            }
        };
        final CacheHandler cacheHandler = new CacheHandler(new DirectBufferCache<CachedHttpRequest>(100, 10000), messageHandler);
        final EncodingHandler handler = new EncodingHandler(cacheHandler);
        handler.addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, new Predicate<HttpServerExchange>() {
            @Override
            public boolean resolve(final HttpServerExchange value) {
                return value.getRequestHeaders().contains(ACTUALLY_DEFLATE);
            }
        });
        DefaultServer.setRootHandler(handler);
    }

    @Test
    public void testCachingWithContentEncoding() throws IOException {
        ContentEncodingHttpClient client = new ContentEncodingHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            //it takes 5 hits to make an entry actually get cached
            for (int i = 1; i <= 5; ++i) {
                HttpResponse result = client.execute(get);
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                Assert.assertEquals("Response " + i, HttpClientUtils.readResponse(result));
                Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
                Assert.assertEquals(0, header.length);
            }

            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
            header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
            header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path2");

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Response 6", HttpClientUtils.readResponse(result));
            header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader("ActuallyDeflate", "true");
            //it takes 5 hits to make an entry actually get cached
            for (int i = 1; i <= 5; ++i) {
                result = client.execute(get);
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
                Assert.assertEquals("deflate", header[0].getValue());
                Assert.assertEquals("Response " + (i + 6), HttpClientUtils.readResponse(result));
            }

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals("deflate", header[0].getValue());
            Assert.assertEquals("Response 11" , HttpClientUtils.readResponse(result));


            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals("deflate", header[0].getValue());
            Assert.assertEquals("Response 11" , HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
