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

package io.undertow.websockets.jsr;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Executor that executes tasks in the order they are submitted, using at most one thread at a time.
 *
 * @author Stuart Douglas
 */
public class OrderedExecutor implements Executor {

    private final Deque<Runnable> tasks = new ConcurrentLinkedDeque<>();
    private final Executor delegate;
    private final ExecutorTask task = new ExecutorTask();
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<OrderedExecutor> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(OrderedExecutor.class, "state");

    private static final int STATE_NOT_RUNNING = 0;
    private static final int STATE_RUNNING = 1;

    public OrderedExecutor(Executor delegate) {
        this.delegate = delegate;
    }


    @Override
    public void execute(Runnable command) {
        tasks.add(command);
        if (stateUpdater.get(this) == STATE_NOT_RUNNING) {
            delegate.execute(task);
        }
    }

    private final class ExecutorTask implements Runnable {

        @Override
        public void run() {
            do {
                //if there is no thread active then we run
                if (stateUpdater.compareAndSet(OrderedExecutor.this, STATE_NOT_RUNNING, STATE_RUNNING)) {
                    Runnable task = tasks.poll();
                    //while the queue is not empty we process in order
                    while (task != null) {
                        try {
                            task.run();
                        } catch (Throwable e) {
                            JsrWebSocketLogger.REQUEST_LOGGER.exceptionInWebSocketMethod(e);
                        }
                        task = tasks.poll();
                    }
                    //set state back to not running.
                    stateUpdater.set(OrderedExecutor.this, STATE_NOT_RUNNING);
                } else {
                    return;
                }
                //we loop again based on tasks not being empty. Otherwise there is a window where the state is running,
                //but poll() has returned null, so a submitting thread will believe that it does not need re-execute.
                //this check fixes the issue
            } while (!tasks.isEmpty());
        }
    }
}
