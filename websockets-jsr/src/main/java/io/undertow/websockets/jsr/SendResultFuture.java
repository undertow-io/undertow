/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of a {@link Future} that can be used to retrieve the {@link SendResult} for an async
 * operation. This implementation also implements {@link SendHandler} which is used to set the {@link SendResult} once
 * it is ready on this future.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class SendResultFuture implements Future<SendResult>, SendHandler {
    private boolean done;
    private SendResult result;
    private int waiters;

    @Override
    public synchronized void setResult(SendResult result) {
        // Allow only once.
        if (done) {
            throw new IllegalStateException();
        }

        done = true;
        if (waiters > 0) {
            notifyAll();
        }
        this.result = result;
    }

    /**
     * Returns {@code true}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public synchronized  boolean isDone() {
        return done;
    }

    @Override
    public SendResult get() throws InterruptedException, ExecutionException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!done) {
                waiters++;
                try {
                    wait();
                } finally {
                    waiters--;
                }
            }
        }
        return result;
    }

    @Override
    public SendResult get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        long timeoutNanos = unit.toNanos(timeout);
        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;

        synchronized (this) {
            if (done) {
                return result;
            }
            if (waitTime <= 0) {
                throw new TimeoutException();
            }

            waiters++;
            try {
                for (; ; ) {
                    wait(waitTime / 1000000, (int) (waitTime % 1000000));

                    if (done) {
                        return result;
                    } else {
                        waitTime = timeoutNanos - (System.nanoTime() - startTime);
                        if (waitTime <= 0) {
                            if (done) {
                                return result;
                            }
                            if (waitTime <= 0) {
                                throw new TimeoutException();
                            }
                        }
                    }
                }
            } finally {
                waiters--;
            }
        }
    }
}
