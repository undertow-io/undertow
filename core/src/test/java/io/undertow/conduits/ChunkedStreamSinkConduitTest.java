/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

package io.undertow.conduits;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Test case for UNDERTOW-2424.
 */
@Category(UnitTest.class)
public class ChunkedStreamSinkConduitTest {

    @Test
    public void testChunkedStreamSinkConduit() throws IOException {
        ByteBufferPool pool = new DefaultByteBufferPool(false, 1024, -1, -1);
        AtomicLong written = new AtomicLong();
        AtomicInteger flushes = new AtomicInteger();
        AtomicInteger listenerInvocations = new AtomicInteger();
        StreamSinkConduit next = new StreamSinkConduit() {

            @Override
            public long transferFrom(FileChannel src, long position, long count) throws IOException {
                written.addAndGet(count);
                return count;
            }

            @Override
            public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
                written.addAndGet(count);
                return count;
            }

            @Override
            public int write(ByteBuffer src) {
                int remaining = src.remaining();
                src.position(src.position() + remaining);
                written.addAndGet(remaining);
                return remaining;
            }

            @Override
            public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
                long total = 0;
                for (int i = offs; i < len; i++) {
                    int written = write(srcs[i]);
                    if (written == 0) {
                        break;
                    }
                    total += written;
                }
                return total;
            }

            @Override
            public int writeFinal(ByteBuffer src) throws IOException {
                return write(src);
            }

            @Override
            public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
                return write(srcs, offset, length);
            }

            @Override
            public void terminateWrites() {
            }

            @Override
            public boolean isWriteShutdown() {
                return false;
            }

            @Override
            public void resumeWrites() {
            }

            @Override
            public void suspendWrites() {
            }

            @Override
            public void wakeupWrites() {
            }

            @Override
            public boolean isWriteResumed() {
                return false;
            }

            @Override
            public void awaitWritable() {
            }

            @Override
            public void awaitWritable(long time, TimeUnit timeUnit) {

            }

            @Override
            public XnioIoThread getWriteThread() {
                return null;
            }

            @Override
            public void setWriteReadyHandler(WriteReadyHandler handler) {

            }

            @Override
            public void truncateWrites() {

            }

            @Override
            public boolean flush() {
                flushes.incrementAndGet();
                return true;
            }

            @Override
            public XnioWorker getWorker() {
                return null;
            }
        };
        ConduitListener<ChunkedStreamSinkConduit> listener = channel -> listenerInvocations.incrementAndGet();
        ChunkedStreamSinkConduit conduit = new ChunkedStreamSinkConduit(next, pool, false, false, new HeaderMap(), listener, new AbstractAttachable() {});

        assertEquals(5, conduit.write(ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8))));
        assertEquals("Expected 11 bytes to be flushed including chunk headers",  11, written.get());
        assertEquals(0, flushes.get());
        conduit.terminateWrites();
        assertTrue(conduit.flush());
        int flushesAfterTerminate = flushes.get();
        assertTrue(conduit.flush());
        // UNDERTOW-2424: If this isn't the case, invocations from response wrappers may invoke flush on persistent
        // connections that are already being used to process other requests on other threads.
        assertEquals("Expected flushing after termination not to have any impact", flushesAfterTerminate, flushes.get());
        assertEquals(1, listenerInvocations.get());
    }
}
