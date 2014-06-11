/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.websockets.client.version13;

import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.SpdyIgnore;
import io.undertow.util.FileUtils;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.server.AutobahnWebSocketServer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.streams.ChannelInputStream;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore
@SpdyIgnore
public class WebSocketClient13TestCase {
    private static XnioWorker worker;

    @BeforeClass
    public static void setup() throws IOException {
        DefaultServer.setRootHandler(AutobahnWebSocketServer.getRootHandler());
        Xnio xnio = Xnio.getInstance(DefaultServer.class.getClassLoader());
        worker = xnio.createWorker(OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 2)
                .set(Options.CONNECTION_HIGH_WATER, 1000000)
                .set(Options.CONNECTION_LOW_WATER, 1000000)
                .set(Options.WORKER_TASK_CORE_THREADS, 30)
                .set(Options.WORKER_TASK_MAX_THREADS, 30)
                .set(Options.TCP_NODELAY, true)
                .set(Options.CORK, true)
                .getMap());

    }

    @AfterClass
    public static void shutdown() {
        worker.shutdown();
    }

    private final Pool<ByteBuffer> buffer = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 1024, 1024);

    @Test
    public void testTextMessage() throws Exception {

        final WebSocketChannel webSocketChannel = WebSocketClient.connect(worker, buffer, OptionMap.EMPTY, new URI(DefaultServer.getDefaultServerURL()), WebSocketVersion.V13).get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        webSocketChannel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
            @Override
            public void handleEvent(final WebSocketChannel channel) {
                ChannelInputStream stream = null;
                try {
                    final StreamSourceFrameChannel r = channel.receive();
                    if (r != null) {
                        stream = new ChannelInputStream(r);
                        result.set(FileUtils.readFile(stream));
                        latch.countDown();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    latch.countDown();
                } finally {
                    IoUtils.safeClose(stream);
                }
            }
        });
        webSocketChannel.resumeReceives();


        StreamSinkFrameChannel sendChannel = webSocketChannel.send(WebSocketFrameType.TEXT, 11);
        new StringWriteChannelListener("Hello World").setup(sendChannel);

        latch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("Hello World", result.get());
        webSocketChannel.sendClose();
    }

}
