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

package tmp.texugo.server.handlers;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;

import static org.xnio.Bits.*;

/**
 * A handler which limits the maximum number of concurrent requests.  Requests beyond the limit will
 * block until the previous request is complete.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RequestLimitingHandler implements HttpHandler {
    @SuppressWarnings("unused")
    private volatile long state;
    private volatile HttpHandler nextHandler;

    private static final AtomicLongFieldUpdater<RequestLimitingHandler> stateUpdater = AtomicLongFieldUpdater.newUpdater(RequestLimitingHandler.class, "state");
    private static final AtomicReferenceFieldUpdater<RequestLimitingHandler, HttpHandler> nextHandlerUpdater = AtomicReferenceFieldUpdater.newUpdater(RequestLimitingHandler.class, HttpHandler.class, "nextHandler");

    private static final long MASK_MAX = longBitMask(32, 63);
    private static final long MASK_CURRENT = longBitMask(0, 30);

    private final LinkedTransferQueue<QueuedRequest> queue = new LinkedTransferQueue<QueuedRequest>();

    /**
     * Construct a new instance. The maximum number of concurrent requests must be at least one.  The next handler
     * must not be {@code null}.
     *
     * @param maximumConcurrentRequests the maximum concurrent requests
     * @param nextHandler the next handler
     */
    public RequestLimitingHandler(int maximumConcurrentRequests, HttpHandler nextHandler) {
        if (nextHandler == null) {
            throw new IllegalArgumentException("nextHandler is null");
        }
        if (maximumConcurrentRequests < 1) {
            throw new IllegalArgumentException("Maximum concurrent requests must be at least 1");
        }
        state = (maximumConcurrentRequests & 0xFFFFFFFFL) << 32;
        this.nextHandler = nextHandler;
    }

    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        long oldVal, newVal;
        do {
            oldVal = state;
            final long current = oldVal & MASK_CURRENT;
            final long max = (oldVal & MASK_MAX) >> 32L;
            if (current >= max) {
                queue.add(new QueuedRequest(exchange, completionHandler));
                return;
            }
            newVal = oldVal + 1;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        nextHandler.handleRequest(exchange, new CompletionHandler(completionHandler, exchange));
    }

    /**
     * Get the maximum concurrent requests.
     *
     * @return the maximum concurrent requests
     */
    public int getMaximumConcurrentRequests() {
        return (int) (state >> 32L);
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
        long oldVal, newVal;
        int current, oldMax;
        do {
            oldVal = state;
            current = (int) (oldVal & MASK_CURRENT);
            oldMax = (int) ((oldVal & MASK_MAX) >> 32L);
            newVal = current | newMax & 0xFFFFFFFFL << 32L;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        while (current < newMax) {
            // more space opened up!  Process queue entries for a while
            final QueuedRequest request = queue.poll();
            if (request != null) {
                // now bump up the counter by one; this *could* put us over the max if it changed in the meantime but that's OK
                newVal = stateUpdater.getAndIncrement(this);
                current = (int) (newVal & MASK_CURRENT);
                request.exchange.getConnection().getWorker().execute(request);
            }
        }
        return oldMax;
    }

    /**
     * Get the next handler.  Will not be {@code null}.
     *
     * @return the next handler
     */
    public HttpHandler getNextHandler() {
        return nextHandler;
    }

    /**
     * Set the next handler.  The value must not be {@code null}.
     *
     * @param nextHandler the next handler
     * @return the old next handler
     */
    public HttpHandler setNextHandler(final HttpHandler nextHandler) {
        if (nextHandler == null) {
            throw new IllegalArgumentException("nextHandler is null");
        }
        return nextHandlerUpdater.getAndSet(this, nextHandler);
    }

    private final class QueuedRequest implements Runnable {
        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;

        QueuedRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
        }

        public void run() {
            nextHandler.handleRequest(exchange, new CompletionHandler(completionHandler, exchange));
        }
    }

    /**
     * Our completion handler.  Put off instantiating as late as possible to maximize chances of being collected by
     * the copying collector.
     */
    private class CompletionHandler implements HttpCompletionHandler {

        private final HttpCompletionHandler completionHandler;
        private final HttpServerExchange exchange;

        public CompletionHandler(final HttpCompletionHandler completionHandler, final HttpServerExchange exchange) {
            this.completionHandler = completionHandler;
            this.exchange = exchange;
        }

        public void handleComplete() {
            try {
                completionHandler.handleComplete();
            } finally {
                final QueuedRequest task = queue.poll();
                if (task != null) {
                    exchange.getConnection().getWorker().execute(task);
                } else {
                    stateUpdater.decrementAndGet(RequestLimitingHandler.this);
                }
            }
        }
    }
}
