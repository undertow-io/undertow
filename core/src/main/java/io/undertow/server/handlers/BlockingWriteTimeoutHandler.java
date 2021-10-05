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
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * BlockingTimeoutHandler allows configurable blocking I/O timeouts for write
 * operations within an exchange.
 * <p>
 * Unlike Options.WRITE_TIMEOUT this only applies to blocking operations which
 * can be helpful to prevent the worker pool from becoming saturated when
 * clients stop responding.
 * <p>
 * When a timeout occurs, a {@link WriteTimeoutException} is thrown, and the
 * {@link ServerConnection} is closed.
 *
 * @author Carter Kozak
 */
public final class BlockingWriteTimeoutHandler implements HttpHandler {

    private final HttpHandler next;
    private final ConduitWrapper<StreamSinkConduit> streamSinkConduitWrapper;

    private BlockingWriteTimeoutHandler(HttpHandler next, Duration writeTimeout) {
        this.next = next;
        this.streamSinkConduitWrapper = new TimeoutStreamSinkConduitWrapper(writeTimeout);
    }

    private static final class TimeoutStreamSinkConduitWrapper implements ConduitWrapper<StreamSinkConduit> {

        private final long timeoutNanoseconds;

        TimeoutStreamSinkConduitWrapper(Duration writeTimeout) {
            this.timeoutNanoseconds = writeTimeout.toNanos();
        }

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            return new TimeoutStreamSinkConduit(factory.create(), exchange.getConnection(), timeoutNanoseconds);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addResponseWrapper(streamSinkConduitWrapper);
        next.handleRequest(exchange);
    }

    private static final class TimeoutStreamSinkConduit implements StreamSinkConduit {

        private final StreamSinkConduit delegate;
        private final ServerConnection serverConnection;
        private final long timeoutNanos;
        private long remaining;

        TimeoutStreamSinkConduit(
                StreamSinkConduit delegate,
                ServerConnection serverConnection,
                long timeoutNanos) {
            this.delegate = delegate;
            this.serverConnection = serverConnection;
            this.timeoutNanos = timeoutNanos;
            this.remaining = timeoutNanos;
        }

        @Override
        public long transferFrom(FileChannel fileChannel, long position, long count) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.transferFrom(fileChannel, position, count));
        }

        @Override
        public long transferFrom(
                StreamSourceChannel streamSourceChannel,
                long count,
                ByteBuffer byteBuffer) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.transferFrom(streamSourceChannel, count, byteBuffer));
        }

        @Override
        public int write(ByteBuffer byteBuffer) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.write(byteBuffer));
        }

        @Override
        public long write(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.write(byteBuffers, offset, length));
        }

        @Override
        public int writeFinal(ByteBuffer byteBuffer) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.writeFinal(byteBuffer));
        }

        @Override
        public long writeFinal(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.writeFinal(byteBuffers, offset, length));
        }

        @Override
        public void terminateWrites() throws IOException {
            delegate.terminateWrites();
        }

        @Override
        public boolean isWriteShutdown() {
            return delegate.isWriteShutdown();
        }

        @Override
        public void resumeWrites() {
            delegate.resumeWrites();
        }

        @Override
        public void suspendWrites() {
            delegate.suspendWrites();
        }

        @Override
        public void wakeupWrites() {
            delegate.wakeupWrites();
        }

        @Override
        public boolean isWriteResumed() {
            return delegate.isWriteResumed();
        }

        @Override
        public void awaitWritable() throws IOException {
            awaitWritable(remaining, TimeUnit.NANOSECONDS);
        }

        @Override
        public void awaitWritable(long duration, TimeUnit unit) throws IOException {
            long startTime = System.nanoTime();
            long requestedNanos = unit.toNanos(duration);
            try {
                delegate.awaitWritable(Math.min(requestedNanos, remaining), TimeUnit.NANOSECONDS);
            } finally {
                remaining -= System.nanoTime() - startTime;
            }
            if (remaining < 0) {
                WriteTimeoutException wte = UndertowMessages.MESSAGES.blockingWriteTimedOut(timeoutNanos);
                UndertowLogger.REQUEST_IO_LOGGER.blockingWriteTimedOut(wte);
                IoUtils.safeClose(serverConnection);
                throw wte;
            }
        }

        @Override
        public XnioIoThread getWriteThread() {
            return delegate.getWriteThread();
        }

        @Override
        public void setWriteReadyHandler(WriteReadyHandler writeReadyHandler) {
            delegate.setWriteReadyHandler(writeReadyHandler);
        }

        @Override
        public void truncateWrites() throws IOException {
            delegate.truncateWrites();
        }

        @Override
        public boolean flush() throws IOException {
            return resetTimeoutIfWriteSucceeded(delegate.flush());
        }

        @Override
        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        private long resetTimeoutIfWriteSucceeded(long value) {
            if (value != 0) {
                // Reset the timeout
                remaining = timeoutNanos;
            }
            return value;
        }

        private int resetTimeoutIfWriteSucceeded(int value) {
            if (value != 0) {
                // Reset the timeout
                remaining = timeoutNanos;
            }
            return value;
        }

        private boolean resetTimeoutIfWriteSucceeded(boolean value) {
            if (value) {
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
        private Duration writeTimeout;

        private Builder() {}

        public Builder writeTimeout(Duration writeTimeout) {
            this.writeTimeout =  checkNotNullParamWithNullPointerException("writeTimeout", writeTimeout);
            return this;
        }

        public Builder nextHandler(HttpHandler nextHandler) {
            this.nextHandler = checkNotNullParamWithNullPointerException("nextHandler", nextHandler);
            return this;
        }

        public HttpHandler build() {
            HttpHandler next = checkNotNullParamWithNullPointerException("nextHandler", nextHandler);
            checkNotNullParam("writeTimeout", writeTimeout);
            if (writeTimeout.isZero() || writeTimeout.isNegative()) {
                throw new IllegalArgumentException("Write timeout must be positive: " + writeTimeout);
            }
            return new BlockingWriteTimeoutHandler(next, writeTimeout);
        }
    }
}
