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

package io.undertow.server;

import io.undertow.conduits.ByteActivityCallback;
import io.undertow.util.StatusCodes;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author Stuart Douglas
 */
public class ConnectorStatisticsImpl implements ConnectorStatistics {

    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> requestCountUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "requestCount");
    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> bytesSentUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "bytesSent");
    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> bytesReceivedUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "bytesReceived");
    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> errorCountUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "errorCount");
    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> processingTimeUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "processingTime");
    private static final AtomicLongFieldUpdater<ConnectorStatisticsImpl> maxProcessingTimeUpdater = AtomicLongFieldUpdater.newUpdater(ConnectorStatisticsImpl.class, "maxProcessingTime");

    private volatile long requestCount;
    private volatile long bytesSent;
    private volatile long bytesReceived;
    private volatile long errorCount;
    private volatile long processingTime;
    private volatile long maxProcessingTime;

    private final ExchangeCompletionListener completionListener = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                if (exchange.getStatusCode() == StatusCodes.INTERNAL_SERVER_ERROR) {
                    errorCountUpdater.incrementAndGet(ConnectorStatisticsImpl.this);

                }
                long start = exchange.getRequestStartTime();
                if (start > 0) {
                    long elapsed = System.currentTimeMillis() - start;
                    processingTimeUpdater.addAndGet(ConnectorStatisticsImpl.this, elapsed);
                    long oldMax;
                    do {
                        oldMax = maxProcessingTime;
                        if (oldMax >= elapsed) {
                            break;
                        }
                    } while (!maxProcessingTimeUpdater.compareAndSet(ConnectorStatisticsImpl.this, oldMax, elapsed));
                }

            } finally {
                nextListener.proceed();
            }
        }
    };

    @Override
    public long getRequestCount() {
        return requestCount;
    }

    @Override
    public long getBytesSent() {
        return bytesSent;
    }

    @Override
    public long getBytesReceived() {
        return bytesReceived;
    }

    @Override
    public long getErrorCount() {
        return errorCount;
    }

    @Override
    public long getProcessingTime() {
        return processingTime;
    }

    @Override
    public long getMaxProcessingTime() {
        return maxProcessingTime;
    }

    @Override
    public void reset() {
        requestCountUpdater.set(this, 0);
        bytesSentUpdater.set(this, 0);
        bytesReceivedUpdater.set(this, 0);
        errorCountUpdater.set(this, 0);
        maxProcessingTimeUpdater.set(this, 0);
        processingTimeUpdater.set(this, 0);
    }

    public void requestFinished(long bytesSent, long bytesReceived, boolean error) {
        bytesSentUpdater.addAndGet(this, bytesSent);
        bytesReceivedUpdater.addAndGet(this, bytesReceived);
        if (error) {
            errorCountUpdater.incrementAndGet(this);
        }
    }

    public void updateBytesSent(long bytes) {
        bytesSentUpdater.addAndGet(this, bytes);
    }

    public void updateBytesReceived(long bytes) {
        bytesReceivedUpdater.addAndGet(this, bytes);
    }

    public void setup(HttpServerExchange exchange) {
        requestCountUpdater.incrementAndGet(this);
        exchange.addExchangeCompleteListener(completionListener);
    }

    public ByteActivityCallback sentAccumulator() {
        return new BytesSentAccumulator();
    }

    public ByteActivityCallback receivedAccumulator() {
        return new BytesReceivedAccumulator();
    }

    //todo: we can do a way
    private class BytesSentAccumulator implements ByteActivityCallback {
        @Override
        public void activity(long bytes) {
            bytesSentUpdater.addAndGet(ConnectorStatisticsImpl.this, bytes);
        }
    }

    private class BytesReceivedAccumulator implements ByteActivityCallback {
        @Override
        public void activity(long bytes) {
            bytesReceivedUpdater.addAndGet(ConnectorStatisticsImpl.this, bytes);
        }
    }
}
