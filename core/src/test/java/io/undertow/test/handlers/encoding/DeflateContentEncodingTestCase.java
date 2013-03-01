package io.undertow.test.handlers.encoding;

import java.io.IOException;
import java.util.Random;

import io.undertow.io.IoCallback;
import io.undertow.predicate.MaxContentSizePredicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DeflateContentEncodingTestCase {

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        final EncodingHandler handler = new EncodingHandler();
        //we don't compress messages 5 bytes or smaller
        handler.addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, new MaxContentSizePredicate(5));
        handler.setNext(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                exchange.getResponseSender().send(message, IoCallback.END_EXCHANGE);
            }
        });

        DefaultServer.setRootHandler(handler);
    }

    /**
     * Tests the use of the deflate contentent encoding
     *
     * @throws IOException
     */
    @Test
    public void testDeflateEncoding() throws IOException {
        runTest("Hello World");
    }


    /**
     * This message should not be compressed as it is too small
     *
     * @throws IOException
     */
    @Test
    public void testSmallMessagePredicateDoesNotCompress() throws IOException {
        ContentEncodingHttpClient client = new ContentEncodingHttpClient();
        try {
            message = "Hi";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "deflate");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);
            final String body = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hi", body);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDeflateEncodingBigResponse() throws IOException {
        final StringBuilder messageBuilder = new StringBuilder(691963);
        for (int i = 0; i < 691963; ++i) {
            messageBuilder.append("*");
        }
        runTest(messageBuilder.toString());
    }

    @Test
    public void testDeflateEncodingRandomSizeResponse() throws IOException {
        int seed = new Random().nextInt();
        System.out.println("Using seed " + seed);
        try {
            final Random random = new Random(seed);
            int size = random.nextInt(691963);
            final StringBuilder messageBuilder = new StringBuilder(size);
            for (int i = 0; i < size; ++i) {
                messageBuilder.append('*' + random.nextInt(10));
            }
            runTest(messageBuilder.toString());
        } catch (Exception e) {
            throw new RuntimeException("Test failed with seed " + seed, e);
        }
    }

    public void runTest(final String theMessage) throws IOException {
        ContentEncodingHttpClient client = new ContentEncodingHttpClient();
        try {
            message = theMessage;
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "deflate");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals("deflate", header[0].getValue());
            final String body = HttpClientUtils.readResponse(result);
            Assert.assertEquals(theMessage, body);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
