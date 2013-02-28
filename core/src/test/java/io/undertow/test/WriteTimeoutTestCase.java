package io.undertow.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.AjpIgnore;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.WriteTimeoutException;

/**
 * Tests read timeout with a client that is slow to read the response
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore
@Ignore("This test fails intermittently")
public class WriteTimeoutTestCase {

    private volatile Exception exception;
    private static final CountDownLatch errorLatch = new CountDownLatch(1);

    @Test
    public void testWriteTimeout() throws IOException, InterruptedException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                final StreamSinkChannel response = exchange.getResponseChannel();
                try {
                    response.setOption(Options.WRITE_TIMEOUT, 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                final int capacity = 1 * 1024 * 1024; //1mb

                final ByteBuffer originalBuffer = ByteBuffer.allocateDirect(capacity);
                for (int i = 0; i < capacity; ++i) {
                    originalBuffer.put((byte) '*');
                }
                originalBuffer.flip();
                response.getWriteSetter().set(new ChannelListener<Channel>() {

                    private ByteBuffer buffer = originalBuffer.duplicate();
                    int count = 0;

                    @Override
                    public void handleEvent(final Channel channel) {
                        do {
                            try {
                                int res = response.write(buffer);
                                if (res == 0) {
                                    return;
                                }
                            } catch (IOException e) {
                                exception = e;
                                errorLatch.countDown();
                            }
                            if(!buffer.hasRemaining()) {
                                count++;
                                buffer = originalBuffer.duplicate();
                            }
                        } while (count < 1000);
                        exchange.endExchange();
                    }
                });
                response.wakeupWrites();
            }
        });

        final TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            try {
                HttpResponse result = client.execute(get);
                InputStream content = result.getEntity().getContent();
                byte[] buffer = new byte[512];
                int r = 0;
                while ((r = content.read(buffer)) > 0) {
                    Thread.sleep(200);
                    if (exception != null) {
                        Assert.assertEquals(WriteTimeoutException.class, exception.getClass());
                        return;
                    }
                }
                Assert.fail("Write did not time out");
            } catch (IOException e) {
                if (errorLatch.await(5, TimeUnit.SECONDS)) {
                    Assert.assertEquals(WriteTimeoutException.class, exception.getClass());
                } else {
                    Assert.fail("Write did not time out");
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
