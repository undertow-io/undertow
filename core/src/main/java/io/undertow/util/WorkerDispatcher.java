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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import org.xnio.channels.StreamSourceChannel;

/**
 * Class that deals with a worker thread pools
 *
 * @author Stuart Douglas
 */
public class WorkerDispatcher {

    private static final ThreadLocal<DispatchData> executingInWorker = new ThreadLocal<DispatchData>();

    public static final AttachmentKey<Executor> EXECUTOR_ATTACHMENT_KEY = AttachmentKey.create(Executor.class);


    /**
     * Dispatches the next request in the current exectutor. If there is no current executor then the
     * channels read thread is used.
     *
     * @param channel  The channel that will be used for the next request
     * @param runnable The task to run
     */
    public static void dispatchNextRequest(final StreamSourceChannel channel, final Runnable runnable) {
        final DispatchData dd = executingInWorker.get();
        if (dd == null) {
            channel.getReadThread().execute(runnable);
        } else {
            dd.tasks.add(runnable);
        }
    }

    private WorkerDispatcher() {

    }

    private static final class DispatchData {
        final Executor executor;
        final Deque<Runnable> tasks = new ArrayDeque<>();

        private DispatchData(Executor executor) {
            this.executor = executor;
        }
    }

    private static class DispatchedRunnable implements Runnable {
        private final Executor executor;
        private final Runnable runnable;

        public DispatchedRunnable(Executor executor, Runnable runnable) {
            this.executor = executor;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            final DispatchData data = new DispatchData(executor);
            try {
                executingInWorker.set(data);
                runnable.run();
            } catch (Exception e) {
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            } finally {
                Runnable next = data.tasks.poll();
                try {
                    while (next != null) {
                        try {
                            next.run();
                        } catch (Exception e) {
                            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
                        }
                        next = data.tasks.poll();
                    }
                } finally {
                    executingInWorker.remove();
                }
            }
        }
    }
}
