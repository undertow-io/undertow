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

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of a {@link Future} that is used in the {@link javax.websocket.RemoteEndpoint.Async}
 * implementation
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class SendResultFuture<T> implements Future<Void>, WebSocketCallback<T> {
    private boolean done;
    private Throwable exception;
    private int waiters;


    @Override
    public synchronized void complete(WebSocketChannel channel, T context) {
        if (done) {
            return;
        }

        if (waiters > 0) {
            notifyAll();
        }
        done = true;
    }

    @Override
    public synchronized void onError(WebSocketChannel channel, T context, Throwable throwable) {
        if (done) {
            return;
        }
        exception = throwable;
        done = true;
        if (waiters > 0) {
            notifyAll();
        }
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
    public synchronized boolean isDone() {
        return done;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
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
        return handleResult();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        long timeoutNanos = unit.toNanos(timeout);
        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;

        synchronized (this) {
            if (done) {
                return handleResult();
            }
            if (waitTime <= 0) {
                throw new TimeoutException();
            }

            waiters++;
            try {
                for (; ; ) {
                    wait(waitTime / 1000000, (int) (waitTime % 1000000));

                    if (done) {
                        return handleResult();
                    } else {
                        waitTime = timeoutNanos - (System.nanoTime() - startTime);
                        if (waitTime <= 0) {
                            if (done) {
                                return handleResult();
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

    private Void handleResult() throws ExecutionException {
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        return null;
    }

}
