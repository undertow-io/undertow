package io.undertow.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.AjpIgnore;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
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
public class WriteTimeoutTestCase {

    private volatile Exception exception;
    private static final CountDownLatch errorLatch = new CountDownLatch(1);

    @Test
    public void testWriteTimeout() throws IOException, InterruptedException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                final StreamSinkChannel response = exchange.getResponseChannelFactory().create();
                try {
                    response.setOption(Options.WRITE_TIMEOUT, 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                final int capacity = 30 * 1024 * 1024; //30mb, should be too big to fit into the network buffer
                final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
                for (int i = 0; i < capacity; ++i) {
                    buffer.put((byte) '*');
                }
                buffer.flip();

                do {
                    try {
                        int res = response.write(buffer);
                        if (res == 0) {
                            response.getWriteSetter().set(new ChannelListener<Channel>() {
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
                                    } while (buffer.hasRemaining());
                                    completionHandler.handleComplete();
                                }
                            });
                            response.resumeWrites();
                            return;
                        }
                    } catch (IOException e) {
                        exception = e;
                        errorLatch.countDown();
                    }
                } while (buffer.hasRemaining());
                completionHandler.handleComplete();
            }
        });

        final TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress());
            try {
                HttpResponse result = client.execute(get);
                InputStream content = result.getEntity().getContent();
                byte[] buffer = new byte[512];
                int r = 0;
                while ((r = content.read(buffer)) > 0) {
                    Thread.sleep(30);
                    if(exception != null) {
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
