package io.undertow.server.handlers;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SenderTestCase {

    public static final int SENDS = 10000;
    public static final int TXS = 1000;
    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() {
        HttpHandler lotsOfSendsHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                boolean blocking = exchange.getQueryParameters().get("blocking").getFirst().equals("true");
                if (blocking) {
                    exchange.startBlocking();
                }
                final Sender sender = exchange.getResponseSender();
                class SendClass implements Runnable, IoCallback {

                    int sent = 0;

                    @Override
                    public void run() {
                        sent++;
                        sender.send("a", this);
                    }

                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        if (sent++ == SENDS) {
                            sender.close();
                            return;
                        }
                        sender.send("a", this);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        exception.printStackTrace();
                        exchange.endExchange();
                    }
                }
                new SendClass().run();
            }
        };
        HttpHandler lotsOfTransferHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                URI uri = SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI();
                File file = new File(uri);
                final FileChannel channel = new FileInputStream(file).getChannel();

                exchange.setResponseContentLength(channel.size() * TXS);

                boolean blocking = exchange.getQueryParameters().get("blocking").getFirst().equals("true");
                if (blocking) {
                    exchange.startBlocking();
                }
                final Sender sender = exchange.getResponseSender();
                class SendClass implements Runnable, IoCallback {

                    int sent = 0;

                    @Override
                    public void run() {
                        sent++;
                        try {
                            channel.position(0);
                        } catch (IOException e) {
                        }
                        sender.transferFrom(channel, this);
                    }

                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        if (sent++ == TXS) {
                            sender.close();
                            return;
                        }
                        try {
                            channel.position(0);
                        } catch (IOException e) {
                        }
                        sender.transferFrom(channel, this);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        exception.printStackTrace();
                        exchange.endExchange();
                    }
                }
                new SendClass().run();
            }
        };

        final HttpHandler fixedLengthSender = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(HELLO_WORLD);
            }
        };

        PathHandler handler = new PathHandler().addPath("/lots", lotsOfSendsHandler)
                                               .addPath("/fixed", fixedLengthSender)
                                               .addPath("/transfer", lotsOfTransferHandler);
        DefaultServer.setRootHandler(handler);
    }


    @Test
    public void testAsyncSender() throws IOException {
        StringBuilder sb = new StringBuilder(SENDS);
        for (int i = 0; i < SENDS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/lots?blocking=false");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sb.toString(), HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAsyncTransfer() throws Exception {
        StringBuilder sb = new StringBuilder(TXS);
        for (int i = 0; i < TXS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/transfer?blocking=false");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            File file = new File(SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI());
            byte[] data = new byte[(int)file.length() * TXS];

            for (int i = 0; i < TXS; i++) {
                DataInputStream is = new DataInputStream(new FileInputStream(file));
                is.readFully(data, (int) (i * file.length()), (int) file.length());
                is.close();
            }
            Assert.assertArrayEquals(data, HttpClientUtils.readRawResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSyncTransfer() throws Exception {
        StringBuilder sb = new StringBuilder(TXS);
        for (int i = 0; i < TXS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/transfer?blocking=true");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            File file = new File(SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI());
            byte[] data = new byte[(int)file.length() * TXS];

            for (int i = 0; i < TXS; i++) {
                DataInputStream is = new DataInputStream(new FileInputStream(file));
                is.readFully(data, (int) (i * file.length()), (int) file.length());
                is.close();
            }
            Assert.assertArrayEquals(data, HttpClientUtils.readRawResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testBlockingSender() throws IOException {
        StringBuilder sb = new StringBuilder(SENDS);
        for (int i = 0; i < SENDS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/lots?blocking=true");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sb.toString(), HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSenderSetsContentLength() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/fixed");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            Assert.assertEquals(1, header.length);
            Assert.assertEquals("" + HELLO_WORLD.length(), header[0].getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
