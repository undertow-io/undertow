/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.ConduitFactory;
import org.xnio.IoUtils;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link BlockingReadTimeoutHandler} allows configurable blocking I/O timeouts
 * for read operations within an exchange.
 * <p>
 * Unlike Options.READ_TIMEOUT this only applies to blocking operations which
 * can be helpful to prevent the worker pool from becoming saturated when
 * clients stop responding.
 * <p>
 * When a timeout occurs, a {@link ReadTimeoutException} is thrown, and the
 * {@link ServerConnection} is closed.
 *
 * @author Carter Kozak
 */
public final class BlockingReadTimeoutHandler implements HttpHandler {

    private final HttpHandler next;
    private final ConduitWrapper<StreamSourceConduit> streamSourceConduitWrapper;

    private BlockingReadTimeoutHandler(HttpHandler next, Duration readTimeout) {
        this.next = next;
        this.streamSourceConduitWrapper = new TimeoutStreamSourceConduitWrapper(readTimeout);
    }

    private static final class TimeoutStreamSourceConduitWrapper implements ConduitWrapper<StreamSourceConduit> {

        private final long timeoutNanoseconds;

        TimeoutStreamSourceConduitWrapper(Duration readTimeout) {
            this.timeoutNanoseconds = readTimeout.toNanos();
        }

        @Override
        public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
            return new TimeoutStreamSourceConduit(factory.create(), exchange.getConnection(), timeoutNanoseconds);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addRequestWrapper(streamSourceConduitWrapper);
        next.handleRequest(exchange);
    }

    private static final class TimeoutStreamSourceConduit implements StreamSourceConduit {
        private final StreamSourceConduit delegate;
        private final ServerConnection serverConnection;
        private final long timeoutNanos;
        private long remaining;

        TimeoutStreamSourceConduit(
                StreamSourceConduit delegate,
                ServerConnection serverConnection,
                long timeoutNanos) {
            this.delegate = delegate;
            this.serverConnection = serverConnection;
            this.timeoutNanos = timeoutNanos;
            this.remaining = timeoutNanos;
        }

        @Override
        public long transferTo(long position, long count, FileChannel fileChannel) throws IOException {
            return resetTimeoutIfReadSucceeded(delegate.transferTo(position, count, fileChannel));
        }

        @Override
        public long transferTo(long count, ByteBuffer byteBuffer, StreamSinkChannel streamSinkChannel) throws IOException {
            return resetTimeoutIfReadSucceeded(delegate.transferTo(count, byteBuffer, streamSinkChannel));
        }

        @Override
        public int read(ByteBuffer byteBuffer) throws IOException {
            return resetTimeoutIfReadSucceeded(delegate.read(byteBuffer));
        }

        @Override
        public long read(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
            return resetTimeoutIfReadSucceeded(delegate.read(byteBuffers, offset, length));
        }

        @Override
        public void terminateReads() throws IOException {
            delegate.terminateReads();
        }

        @Override
        public boolean isReadShutdown() {
            return delegate.isReadShutdown();
        }

        @Override
        public void resumeReads() {
            delegate.resumeReads();
        }

        @Override
        public void suspendReads() {
            delegate.suspendReads();
        }

        @Override
        public void wakeupReads() {
            delegate.wakeupReads();
        }

        @Override
        public boolean isReadResumed() {
            return delegate.isReadResumed();
        }

        @Override
        public void awaitReadable() throws IOException {
            awaitReadable(remaining, TimeUnit.NANOSECONDS);
        }

        @Override
        public void awaitReadable(long duration, TimeUnit unit) throws IOException {
            long startTime = System.nanoTime();
            long requestedNanos = unit.toNanos(duration);
            try {
                delegate.awaitReadable(Math.min(requestedNanos, remaining), TimeUnit.NANOSECONDS);
            } finally {
                remaining -= System.nanoTime() - startTime;
            }
            if (remaining < 0) {
                ReadTimeoutException rte = UndertowMessages.MESSAGES.blockingReadTimedOut(timeoutNanos);
                UndertowLogger.REQUEST_IO_LOGGER.blockingReadTimedOut(rte);
                IoUtils.safeClose(serverConnection);
                throw rte;
            }
        }

        @Override
        public XnioIoThread getReadThread() {
            return delegate.getReadThread();
        }

        @Override
        public void setReadReadyHandler(ReadReadyHandler readReadyHandler) {
            delegate.setReadReadyHandler(readReadyHandler);
        }

        @Override
        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        private long resetTimeoutIfReadSucceeded(long value) {
            if (value != 0) {
                // Reset the timeout
                remaining = timeoutNanos;
            }
            return value;
        }

        private int resetTimeoutIfReadSucceeded(int value) {
            if (value != 0) {
                // Reset the timeout
                remaining = timeoutNanos;
            }
            return value;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private HttpHandler nextHandler;
        private Duration readTimeout;

        private Builder() {}

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = checkNotNullParamWithNullPointerException("readTimeout", readTimeout);
            return this;
        }

        public Builder nextHandler(HttpHandler nextHandler) {
            this.nextHandler = checkNotNullParamWithNullPointerException("nextHandler", nextHandler);
            return this;
        }

        public HttpHandler build() {
            HttpHandler next = checkNotNullParamWithNullPointerException("nextHandler", nextHandler);
            checkNotNullParam("readTimeout", readTimeout);
            if (readTimeout.isZero() || readTimeout.isNegative()) {
                throw new IllegalArgumentException("Read timeout must be positive: " + readTimeout);
            }
            return new BlockingReadTimeoutHandler(next, readTimeout);
        }
    }
}
