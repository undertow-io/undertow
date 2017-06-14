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

    private volatile boolean shutdown = false;
    private final GracefulShutdownListener listener = new GracefulShutdownListener();
    private final List<ShutdownListener> shutdownListeners = new ArrayList<>();

    private final Object lock = new Object();

    private volatile long activeRequests = 0;
    private static final AtomicLongFieldUpdater<GracefulShutdownHandler> activeRequestsUpdater = AtomicLongFieldUpdater.newUpdater(GracefulShutdownHandler.class, "activeRequests");

    private final HttpHandler next;

    public GracefulShutdownHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        activeRequestsUpdater.incrementAndGet(this);
        if (shutdown) {
            decrementRequests();
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            exchange.endExchange();
            return;
        }
        exchange.addExchangeCompleteListener(listener);
        next.handleRequest(exchange);
    }


    public void shutdown() {
        activeRequestsUpdater.incrementAndGet(this);
        //the request count is never zero when shutdown is set to true
        shutdown = true;
        decrementRequests();
    }

    public void start() {
        synchronized (lock) {
            shutdown = false;
            for (ShutdownListener listener : shutdownListeners) {
                listener.shutdown(false);
            }
            shutdownListeners.clear();
        }
    }

    private void shutdownComplete() {
        assert Thread.holdsLock(lock);
        lock.notifyAll();
        for (ShutdownListener listener : shutdownListeners) {
            listener.shutdown(true);
        }
        shutdownListeners.clear();
    }

    /**
     * Waits for the handler to shutdown.
     */
    public void awaitShutdown() throws InterruptedException {
        synchronized (lock) {
            if (!shutdown) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            while (activeRequestsUpdater.get(this) > 0) {
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
            if (!shutdown) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            long end = System.currentTimeMillis() + millis;
            int count = (int) activeRequestsUpdater.get(this);
            while (count != 0) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                lock.wait(left);
                count = (int) activeRequestsUpdater.get(this);
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
            if (!shutdown) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            long count = activeRequestsUpdater.get(this);
            if (count == 0) {
                shutdownListener.shutdown(true);
            } else {
                shutdownListeners.add(shutdownListener);
            }
        }
    }


    private void decrementRequests() {
        if (shutdown) {
            //we don't read the request count until after checking the shutdown variable
            //otherwise we could read the request count as zero, a new request could state, and then we shutdown
            //see https://issues.jboss.org/browse/UNDERTOW-1099
            long active = activeRequestsUpdater.decrementAndGet(this);
            synchronized (lock) {
                if (active == 0) {
                    shutdownComplete();
                }
            }
        } else {
            activeRequestsUpdater.decrementAndGet(this);
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
