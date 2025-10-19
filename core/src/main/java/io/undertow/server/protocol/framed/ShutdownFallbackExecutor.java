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
package io.undertow.server.protocol.framed;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link ShutdownFallbackExecutor} wrapper around a single threaded executor
 * which will execute pending tasks when the worker has been shut down.
 */
final class ShutdownFallbackExecutor {
    private static volatile Executor EXECUTOR = null;
    private ShutdownFallbackExecutor() {
        // Static Utility
    }

    static void execute(Runnable runnable) {
        if (EXECUTOR == null) {
            synchronized (ShutdownFallbackExecutor.class) {
                if (EXECUTOR == null) {
                    EXECUTOR = new ThreadPoolExecutor(0, 1,
                            10, TimeUnit.MILLISECONDS,
                            new LinkedBlockingQueue<>(),
                            new ShutdownFallbackThreadFactory());
                }
            }
        }
        EXECUTOR.execute(runnable);
    }

    static final class ShutdownFallbackThreadFactory implements ThreadFactory {
        private final AtomicLong count = new AtomicLong();
        private final ThreadFactory threadFactory = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = threadFactory.newThread(r);
            thread.setName("undertow-shutdown-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
