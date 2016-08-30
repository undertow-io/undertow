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

package io.undertow.server.handlers;

import io.undertow.server.Connectors;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Represents a limit on a number of running requests.
 * <p>
 * This is basically a counter with a configured set of limits, that is used by {@link RequestLimitingHandler}.
 * <p>
 * When the number of active requests goes over the configured max requests then requests will be suspended and queued.
 * <p>
 * If the queue is full requests will be rejected with a 513.
 * <p>
 * The reason why this is abstracted out into a separate class is so that multiple handlers can share the same state. This
 * allows for fine grained control of resources.
 *
 * @author Stuart Douglas
 * @see RequestLimitingHandler
 */
public class RequestLimit {
    @SuppressWarnings("unused")
    private volatile int requests;
    private volatile int max;

    private static final AtomicIntegerFieldUpdater<RequestLimit> requestsUpdater = AtomicIntegerFieldUpdater.newUpdater(RequestLimit.class, "requests");


    /**
     * The handler that will be invoked if the queue is full.
     */
    private volatile HttpHandler failureHandler = new ResponseCodeHandler(513);

    private final Queue<SuspendedRequest> queue;

    private final ExchangeCompletionListener COMPLETION_LISTENER = new ExchangeCompletionListener() {

        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            try {
                synchronized (RequestLimit.this) {
                    final SuspendedRequest task = queue.poll();
                    if (task != null) {
                        task.exchange.addExchangeCompleteListener(COMPLETION_LISTENER);
                        task.exchange.dispatch(task.next);
                    } else {
                        decrementRequests();
                    }
                }
            } finally {
                nextListener.proceed();
            }
        }
    };


    public RequestLimit(int maximumConcurrentRequests) {
        this(maximumConcurrentRequests, -1);
    }

    /**
     * Construct a new instance. The maximum number of concurrent requests must be at least one.
     *
     * @param maximumConcurrentRequests the maximum concurrent requests
     * @param queueSize                 The maximum number of requests to queue
     */
    public RequestLimit(int maximumConcurrentRequests, int queueSize) {
        if (maximumConcurrentRequests < 1) {
            throw new IllegalArgumentException("Maximum concurrent requests must be at least 1");
        }
        max = maximumConcurrentRequests;

        this.queue = new LinkedBlockingQueue<>(queueSize <= 0 ? Integer.MAX_VALUE : queueSize);
    }

    public void handleRequest(final HttpServerExchange exchange, final HttpHandler next) throws Exception {
        int oldVal, newVal;
        do {
            oldVal = requests;
            if (oldVal >= max) {
                exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
                    @Override
                    public void run() {
                        //we have to try again in the sync block
                        //we need to have already dispatched for thread safety reasons
                        synchronized (RequestLimit.this) {
                            int oldVal, newVal;
                            do {
                                oldVal = requests;
                                if (oldVal >= max) {
                                    if (!queue.offer(new SuspendedRequest(exchange, next))) {
                                        Connectors.executeRootHandler(failureHandler, exchange);
                                    }
                                    return;
                                }
                                newVal = oldVal + 1;
                            } while (!requestsUpdater.compareAndSet(RequestLimit.this, oldVal, newVal));
                            exchange.addExchangeCompleteListener(COMPLETION_LISTENER);
                            exchange.dispatch(next);
                        }
                    }
                });
                return;
            }
            newVal = oldVal + 1;
        } while (!requestsUpdater.compareAndSet(this, oldVal, newVal));
        exchange.addExchangeCompleteListener(COMPLETION_LISTENER);
        next.handleRequest(exchange);
    }

    /**
     * Get the maximum concurrent requests.
     *
     * @return the maximum concurrent requests
     */
    public int getMaximumConcurrentRequests() {
        return max;
    }

    /**
     * Set the maximum concurrent requests.  The value must be greater than or equal to one.
     *
     * @param newMax the maximum concurrent requests
     */
    public int setMaximumConcurrentRequests(int newMax) {
        if (newMax < 1) {
            throw new IllegalArgumentException("Maximum concurrent requests must be at least 1");
        }
        int oldMax = this.max;
        this.max = newMax;
        if(newMax > oldMax) {
            synchronized (this) {
                while (!queue.isEmpty()) {
                    int oldVal, newVal;
                    do {
                        oldVal = requests;
                        if (oldVal >= max) {
                            return oldMax;
                        }
                        newVal = oldVal + 1;
                    } while (!requestsUpdater.compareAndSet(this, oldVal, newVal));
                    SuspendedRequest res = queue.poll();
                    res.exchange.dispatch(res.next);
                }
            }
        }
        return oldMax;
    }

    private void decrementRequests() {
        requestsUpdater.decrementAndGet(this);
    }

    public HttpHandler getFailureHandler() {
        return failureHandler;
    }

    public void setFailureHandler(HttpHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    private static final class SuspendedRequest {
        final HttpServerExchange exchange;
        final HttpHandler next;

        private SuspendedRequest(HttpServerExchange exchange, HttpHandler next) {
            this.exchange = exchange;
            this.next = next;
        }
    }
}
