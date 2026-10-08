/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.testutils.category.UnitTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(UnitTest.class)
public class DefaultByteBufferPoolTestCase {

    @Test
    public void cachedLookupDoesNotRequireRegistryMonitor() throws Exception {
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024, 64, 6);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> cycle(pool)).get(5, TimeUnit.SECONDS);
            synchronized (registry(pool)) {
                worker.submit(() -> cycle(pool)).get(5, TimeUnit.SECONDS);
            }
        } finally {
            worker.shutdownNow();
            pool.close();
        }
    }

    @Test
    public void crossThreadReleasePreservesOutstandingBuffers() throws Exception {
        ExecutorService releaser = Executors.newSingleThreadExecutor();
        try {
            for (boolean direct : new boolean[] {false, true}) {
                for (int cacheSize : new int[] {0, 6}) {
                    DefaultByteBufferPool pool = new DefaultByteBufferPool(direct, 1024, 64, cacheSize);
                    List<PooledByteBuffer> outstanding = new ArrayList<>();
                    List<PooledByteBuffer> reused = new ArrayList<>();
                    try {
                        for (int i = 0; i < 20; i++) {
                            PooledByteBuffer buffer = pool.allocate();
                            outstanding.add(buffer);
                            buffer.getBuffer().putInt(i);
                        }
                        for (int i = 0; i < outstanding.size(); i++) {
                            assertEquals(i, outstanding.get(i).getBuffer().getInt(0));
                        }
                        releaser.submit(() -> outstanding.subList(0, 10).forEach(PooledByteBuffer::close))
                                .get(5, TimeUnit.SECONDS);
                        for (int i = 0; i < 10; i++) {
                            PooledByteBuffer buffer = pool.allocate();
                            reused.add(buffer);
                            assertEquals(0, buffer.getBuffer().position());
                            buffer.getBuffer().putInt(100 + i);
                        }
                        for (int i = 0; i < reused.size(); i++) {
                            assertEquals(100 + i, reused.get(i).getBuffer().getInt(0));
                        }
                        for (int i = 10; i < 20; i++) {
                            assertEquals(i, outstanding.get(i).getBuffer().getInt(0));
                        }
                    } finally {
                        outstanding.forEach(PooledByteBuffer::close);
                        reused.forEach(PooledByteBuffer::close);
                        pool.close();
                        pool.getArrayBackedPool().close();
                    }
                }
            }
        } finally {
            releaser.shutdownNow();
        }
    }

    @Test
    public void poolCloseFromAnotherThreadPreservesOutstandingBuffer() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            for (boolean direct : new boolean[] {false, true}) {
                DefaultByteBufferPool pool = new DefaultByteBufferPool(direct, 1024, 64, 6);
                try {
                    worker.submit(() -> cycle(pool)).get(5, TimeUnit.SECONDS);
                    PooledByteBuffer outstanding = worker.submit(pool::allocate).get(5, TimeUnit.SECONDS);
                    try {
                        outstanding.getBuffer().putInt(17);
                        pool.close();
                        assertEquals(17, outstanding.getBuffer().getInt(0));
                        assertTrue(registry(pool).isEmpty());
                        worker.submit(() -> {
                            try {
                                pool.allocate();
                                fail("Closed pool accepted an allocation");
                            } catch (IllegalStateException expected) {
                            }
                        }).get(5, TimeUnit.SECONDS);
                    } finally {
                        outstanding.close();
                        outstanding.close();
                    }
                    assertFalse(outstanding.isOpen());
                } finally {
                    pool.close();
                    pool.getArrayBackedPool().close();
                }
            }
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void livePoolKeepsCachedBufferAcrossGc() {
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024, 64, 6);
        try {
            WeakReference<ByteBuffer> cached;
            try (PooledByteBuffer buffer = pool.allocate()) {
                cached = new WeakReference<>(buffer.getBuffer());
            }
            System.gc();
            assertNotNull(cached.get());
            try (PooledByteBuffer buffer = pool.allocate()) {
                assertSame(cached.get(), buffer.getBuffer());
            }
        } finally {
            pool.close();
        }
    }

    @Test
    public void liveWorkerDoesNotRetainDiscardedPool() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            for (boolean close : new boolean[] {false, true}) {
                WeakReference<DefaultByteBufferPool> reference = worker.submit(() -> discardedPool(close))
                        .get(5, TimeUnit.SECONDS);
                awaitCollected(reference, () -> { });
                assertTrue(worker.submit(() -> true).get(5, TimeUnit.SECONDS));
            }
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void steadyTrafficReleasesRetiredThreadsCache() throws Exception {
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024, 64, 6);
        try {
            cycle(pool);
            List<WeakReference<?>> references = retiredThread(pool);
            Runnable traffic = () -> {
                for (int i = 0; i < 256; i++) {
                    cycle(pool);
                }
            };
            awaitCollected(references.get(0), traffic);
            awaitCollected(references.get(1), traffic);
        } finally {
            pool.close();
        }
    }

    private static void cycle(DefaultByteBufferPool pool) {
        try (PooledByteBuffer buffer = pool.allocate()) {
            assertEquals(0, buffer.getBuffer().position());
            buffer.getBuffer().putInt(123);
            assertEquals(123, buffer.getBuffer().getInt(0));
        }
    }

    private static WeakReference<DefaultByteBufferPool> discardedPool(boolean close) {
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024, 64, 6);
        cycle(pool);
        if (close) {
            pool.close();
        }
        return new WeakReference<>(pool);
    }

    private static List<WeakReference<?>> retiredThread(DefaultByteBufferPool pool) throws Exception {
        FutureTask<WeakReference<?>> task = new FutureTask<>(() -> {
            cycle(pool);
            Object data = registry(pool).get(Thread.currentThread());
            assertNotNull(data);
            return new WeakReference<>(data);
        });
        Thread thread = new Thread(task);
        thread.start();
        WeakReference<?> data = task.get(5, TimeUnit.SECONDS);
        thread.join(5000);
        assertFalse(thread.isAlive());
        return List.of(new WeakReference<>(thread), data);
    }

    private static void awaitCollected(WeakReference<?> reference, Runnable traffic) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            traffic.run();
            Thread.sleep(20);
        }
        assertNull("Retained object after repeated GC and pool traffic", reference.get());
    }

    private static Map<?, ?> registry(DefaultByteBufferPool pool) throws Exception {
        Field cacheField = DefaultByteBufferPool.class.getDeclaredField("threadLocalCache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(pool);
        Field registryField = cache.getClass().getDeclaredField("localsByThread");
        registryField.setAccessible(true);
        return (Map<?, ?>) registryField.get(cache);
    }
}
