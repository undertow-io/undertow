/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import io.undertow.server.HttpServerExchange;
import org.xnio.channels.StreamSourceChannel;

/**
 * Class that deals with a worker thread pools
 *
 * @author Stuart Douglas
 */
public class WorkerDispatcher {

    private static final ThreadLocal<Executor> executingInWorker = new ThreadLocal<Executor>();

    public static final AttachmentKey<Executor> EXECUTOR_ATTACHMENT_KEY = AttachmentKey.create(Executor.class);

    public static void dispatch(final HttpServerExchange exchange, final Runnable runnable) {
        Executor executor = exchange.getAttachment(EXECUTOR_ATTACHMENT_KEY);
        if (executor == null) {
            executor = exchange.getConnection().getWorker();
        }
        final Executor executing = executingInWorker.get();
        if (executing == executor) {
            runnable.run();
        } else {
            final Executor e = executor;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        executingInWorker.set(e);
                        runnable.run();
                    } finally {
                        executingInWorker.remove();
                    }
                }
            });
        }
    }

    /**
     * Forces a task dispatch with the specified executor
     * @param executor The executor to use
     * @param runnable The runnable
     */
    public static void dispatch(final Executor executor, final Runnable runnable) {
        final Executor e = executor;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executingInWorker.set(e);
                    runnable.run();
                } finally {
                    executingInWorker.remove();
                }
            }
        });
    }

    /**
     * Dispatches the next request in the current exectutor. If there is no current executor then the
     * channels read thread is used.
     *
     * @param channel  The channel that will be used for the next request
     * @param runnable The task to run
     */
    public static void dispatchNextRequest(final StreamSourceChannel channel, final Runnable runnable) {
        final Executor executing = executingInWorker.get();
        if (executing == null) {
            channel.getReadThread().execute(runnable);
        } else {
            executing.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        executingInWorker.set(executing);
                        runnable.run();
                    } finally {
                        executingInWorker.remove();
                    }
                }
            });
        }
    }

    private WorkerDispatcher() {

    }
}
