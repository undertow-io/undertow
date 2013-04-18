/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import org.xnio.XnioWorker;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Special {@link Executor} which guarantee the serial processing of the WebSocket frames and calling the
 * {@link io.undertow.websockets.api.FrameHandler} methods for them per {@link io.undertow.websockets.api.WebSocketSession}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class FrameHandlerExecutor implements Executor {
    private final XnioWorker worker;
    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();

    private boolean running = false;
    private final Runnable requestRunnable = new Runnable() {

        @Override
        public void run() {
            for (;;) {
                final Runnable task;
                synchronized (tasks) {
                    running = true;
                    task = tasks.poll();
                }
                if (task != null) {
                    task.run();
                }
                synchronized (tasks) {
                    if (tasks.isEmpty()) {
                        running = false;
                        break;
                    }
                }
            }
        }
    };

    public FrameHandlerExecutor(XnioWorker worker) {
        this.worker = worker;
    }

    @Override
    public void execute(Runnable command) {

        synchronized (tasks) {
            tasks.add(command);
            if (!running) {
                worker.execute(requestRunnable);
            }
        }
    }
}
