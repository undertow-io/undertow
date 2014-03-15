package io.undertow.server.spdy;

import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.protocol.spdy.SpdyOpenListener;
import io.undertow.testutils.DefaultServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListeners;

import java.io.File;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleSpdyTestCase {

    static SpdyOpenListener openListener;
    static int server;

    @BeforeClass
    public static void setup() throws IOException {
        ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 8024, 8024);
        openListener = new SpdyOpenListener(pool, pool, DefaultServer.getUndertowOptions(), 8024);
        openListener.setRootHandler(new ResourceHandler(new FileResourceManager(new File("/"), 100)).setDirectoryListingEnabled(true));
        DefaultServer.startSSLServer(DefaultServer.getUndertowOptions(), ChannelListeners.openListenerAdapter(openListener));
    }

    @AfterClass
    public static void teardown() throws IOException {
        DefaultServer.stopSSLServer();
    }


    @Test
    public void testSpdy() throws InterruptedException {
       //Thread.sleep(10000000);
    }
}
