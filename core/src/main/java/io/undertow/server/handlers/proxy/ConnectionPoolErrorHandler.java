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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy;

/**
 * The connection pool error handler is intended to be used per node and will therefore be shared across I/O threads.
 *
 * @author Emanuel Muckenhuber
 */
public interface ConnectionPoolErrorHandler {

    /**
     * Check whether pool is available
     *
     * @return whether the pool is available
     */
    boolean isAvailable();

    /**
     * Handle a connection error.
     *
     * @return {@code true} if the pool is still available, {@code false} otherwise
     */
    boolean handleError();

    /**
     * Clear the connection errors.
     *
     * @return {@code true} if the pool is available again, {@code false} otherwise
     */
    boolean clearError();

    class SimpleConnectionPoolErrorHandler implements ConnectionPoolErrorHandler {

        private volatile boolean problem;

        @Override
        public boolean isAvailable() {
            return !problem;
        }

        @Override
        public boolean handleError() {
            problem = true;
            return false;
        }

        @Override
        public boolean clearError() {
            problem = false;
            return true;
        }
    }

    /**
     * Counting error handler, this only propagates the state to the delegate handler after reaching a given limit.
     */
    class CountingErrorHandler implements ConnectionPoolErrorHandler {

        private int count;
        private long timeout;

        private final long interval;
        private final int errorCount;
        private final int successCount;
        private final ConnectionPoolErrorHandler delegate;

        public CountingErrorHandler(int errorCount, int successCount, long interval) {
            this(errorCount, successCount, interval, new SimpleConnectionPoolErrorHandler());
        }

        public CountingErrorHandler(int errorCount, int successCount, long interval, ConnectionPoolErrorHandler delegate) {
            this.errorCount = Math.max(errorCount, 1);
            this.successCount = Math.max(successCount, 1);
            this.interval = Math.max(interval, 0);
            this.delegate = delegate;
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }

        @Override
        public synchronized boolean handleError() {
            if (delegate.isAvailable()) {
                final long time = System.currentTimeMillis();
                // If the timeout is reached reset the error count
                if (time >= timeout) {
                    count = 1;
                    timeout = time + interval;
                } else {
                    if (count++ == 1) {
                        timeout = time + interval;
                    }
                }
                if (count >= errorCount) {
                    return delegate.handleError();
                }
                return true;
            } else {
                count = 0; // if in error reset the successful count
                return false;
            }
        }

        @Override
        public synchronized boolean clearError() {
            if (delegate.isAvailable()) {
                count = 0; // Just reset the error count
                return true;
            } else {
                // Count the successful attempts
                if (count++ == successCount) {
                    return delegate.clearError();
                }
                return false;
            }
        }
    }

}
