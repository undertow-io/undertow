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

import io.undertow.UndertowMessages;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.LongUnaryOperator;

/**
 * Handler that allows for graceful server shutdown. Basically it provides a way to prevent the server from
 * accepting new requests, and wait for existing requests to complete.
 * <p>
 * The handler itself does not shut anything down.
 * <p>
 * Import: The thread safety semantics of the handler are very important. Don't touch anything unless you know
 * what you are doing.
 *
 * @author Stuart Douglas
 */
public class GracefulShutdownHandler implements HttpHandler {

    private static final long SHUTDOWN_MASK = 1L << 63;
    private static final long ACTIVE_COUNT_MASK = (1L << 63) - 1;

    private static final LongUnaryOperator incrementActive = current -> {
        long incrementedActiveCount = activeCount(current) + 1;
        return incrementedActiveCount | (current & ~ACTIVE_COUNT_MASK);
    };

    private static final LongUnaryOperator incrementActiveAndShutdown =
            incrementActive.andThen(current -> current | SHUTDOWN_MASK);

    private static final LongUnaryOperator decrementActive = current -> {
        long decrementedActiveCount = activeCount(current) - 1;
        return decrementedActiveCount | (current & ~ACTIVE_COUNT_MASK);
    };

    private final GracefulShutdownListener listener = new GracefulShutdownListener();
    private final List<ShutdownListener> shutdownListeners = new ArrayList<>();

    private final Object lock = new Object();

    private volatile long state = 0;
    private static final AtomicLongFieldUpdater<GracefulShutdownHandler> stateUpdater =
            AtomicLongFieldUpdater.newUpdater(GracefulShutdownHandler.class, "state");

    private final HttpHandler next;

    public GracefulShutdownHandler(HttpHandler next) {
        this.next = next;
    }

    private static boolean isShutdown(long state) {
        return (state & SHUTDOWN_MASK) != 0;
    }

    private static long activeCount(long state) {
        return state & ACTIVE_COUNT_MASK;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        long snapshot = stateUpdater.updateAndGet(this, incrementActive);
        if (isShutdown(snapshot)) {
            decrementRequests();
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            exchange.endExchange();
            return;
        }
        exchange.addExchangeCompleteListener(listener);
        next.handleRequest(exchange);
    }


    public void shutdown() {
        //the request count is never zero when shutdown is set to true
        stateUpdater.updateAndGet(this, incrementActiveAndShutdown);
        decrementRequests();
    }

    public void start() {
        synchronized (lock) {
            stateUpdater.updateAndGet(this, current -> current & ACTIVE_COUNT_MASK);
            for (ShutdownListener listener : shutdownListeners) {
                listener.shutdown(false);
            }
            shutdownListeners.clear();
        }
    }

    private void shutdownComplete() {
        synchronized (lock) {
            lock.notifyAll();
            for (ShutdownListener listener : shutdownListeners) {
                listener.shutdown(true);
            }
            shutdownListeners.clear();
        }
    }

    /**
     * Waits for the handler to shutdown.
     */
    public void awaitShutdown() throws InterruptedException {
        synchronized (lock) {
            if (!isShutdown(stateUpdater.get(this))) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            while (activeCount(stateUpdater.get(this)) > 0) {
                lock.wait();
            }
        }
    }

    /**
     * Waits a set length of time for the handler to shut down
     *
     * @param millis The length of time
     * @return <code>true</code> If the handler successfully shut down
     */
    public boolean awaitShutdown(long millis) throws InterruptedException {
        synchronized (lock) {
            if (!isShutdown(stateUpdater.get(this))) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            long end = System.currentTimeMillis() + millis;
            while (activeCount(stateUpdater.get(this)) != 0) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                lock.wait(left);
            }
            return true;
        }
    }

    /**
     * Adds a shutdown listener that will be invoked when all requests have finished. If all requests have already been finished
     * the listener will be invoked immediately.
     *
     * @param shutdownListener The shutdown listener
     */
    public void addShutdownListener(final ShutdownListener shutdownListener) {
        synchronized (lock) {
            if (!isShutdown(stateUpdater.get(this))) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            long count = activeCount(stateUpdater.get(this));
            if (count == 0) {
                shutdownListener.shutdown(true);
            } else {
                shutdownListeners.add(shutdownListener);
            }
        }
    }

    private void decrementRequests() {
        long snapshot = stateUpdater.updateAndGet(this, decrementActive);
        // Shutdown has completed when the activeCount portion is zero, and shutdown is set.
        if (snapshot == SHUTDOWN_MASK) {
            shutdownComplete();
        }
    }

    private final class GracefulShutdownListener implements ExchangeCompletionListener {

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                decrementRequests();
            } finally {
                nextListener.proceed();
            }
        }
    }

    /**
     * A listener which can be registered with the handler to be notified when all pending requests have finished.
     */
    public interface ShutdownListener {

        /**
         * Notification that the container has shutdown.
         *
         * @param shutdownSuccessful If the shutdown succeeded or not
         */
        void shutdown(boolean shutdownSuccessful);
    }
}
