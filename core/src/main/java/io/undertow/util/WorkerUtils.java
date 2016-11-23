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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import io.undertow.UndertowLogger;

/**
 * @author Stuart Douglas
 */
public class WorkerUtils {

    private WorkerUtils() {
    }

    /**
     * Schedules a task for future execution. If the execution is rejected because the worker is shutting
     * down then it is logged at debug level and the exception is not re-thrown
     *  @param thread   The IO thread
     * @param task     The task to execute
     * @param timeout  The timeout
     * @param timeUnit The time unit
     */
    public static XnioExecutor.Key executeAfter(XnioIoThread thread, Runnable task, long timeout, TimeUnit timeUnit) {
        try {
            return thread.executeAfter(task, timeout, timeUnit);
        } catch (RejectedExecutionException e) {
            if(thread.getWorker().isShutdown()) {
                UndertowLogger.ROOT_LOGGER.debugf(e, "Failed to schedule task %s as worker is shutting down", task);
                //we just return a bogus key in this case
                return new XnioExecutor.Key() {
                    @Override
                    public boolean remove() {
                        return false;
                    }
                };
            } else {
                throw e;
            }
        }
    }
}
