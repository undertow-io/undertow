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

/**
 *
 * Class that deals with a worker thread pools
 *
 * @author Stuart Douglas
 */
public class WorkerDispatcher {

    private static final ThreadLocal<Boolean> executingInWorker = new ThreadLocal<Boolean>();

    public static final AttachmentKey<Executor> EXECUTOR_ATTACHMENT_KEY = AttachmentKey.create(Executor.class);

    public static void dispatch( final HttpServerExchange exchange, final Runnable runnable) {
        Executor executor = exchange.getAttachment(EXECUTOR_ATTACHMENT_KEY);
        Boolean executing = executingInWorker.get();
        if (executing != null && executing) {
            runnable.run();
        } else {
            (executor != null ? executor : exchange.getConnection().getWorker()).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        executingInWorker.set(true);
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
