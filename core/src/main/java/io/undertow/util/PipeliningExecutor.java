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

package io.undertow.util;

import io.undertow.UndertowLogger;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Executor that will continue to re-run tasks in a loop that are submitted from its own thread.
 *
 * @author Stuart Douglas
 */
@Deprecated
public class PipeliningExecutor implements Executor {

    private final Executor executor;

    private static final ThreadLocal<LinkedList<Runnable>> THREAD_QUEUE = new ThreadLocal<>();

    public PipeliningExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(final Runnable command) {
        List<Runnable> queue = THREAD_QUEUE.get();
        if (queue != null) {
            queue.add(command);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    LinkedList<Runnable> queue = THREAD_QUEUE.get();
                    if (queue == null) {
                        THREAD_QUEUE.set(queue = new LinkedList<>());
                    }
                    try {
                        command.run();
                    } catch (Throwable t) {
                        UndertowLogger.REQUEST_LOGGER.debugf(t, "Task %s failed", command);
                    }
                    Runnable runnable = queue.poll();
                    while (runnable != null) {
                        try {
                            runnable.run();
                        } catch (Throwable t) {
                            UndertowLogger.REQUEST_LOGGER.debugf(t, "Task %s failed", command);
                        }
                        runnable = queue.poll();
                    }

                }
            });
        }
    }
}
