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

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import org.xnio.IoUtils;
import org.xnio.XnioExecutor;

import java.util.concurrent.TimeUnit;

/**
 * Wrapper for parse timeout.
 *
 * @author Sebastian Laskawiec
 * @see io.undertow.UndertowOptions#REQUEST_PARSE_TIMEOUT
 */
final class ParseTimeoutUpdater {

    private final HttpServerConnection connection;
    private final int requestParseTimeout;
    private volatile XnioExecutor.Key handle;
    private volatile long expireTime = -1;

    //we add 50ms to the timeout to make sure the underlying channel has actually timed out
    private static final int FUZZ_FACTOR = 50;

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            handle = null;
            if (shouldPerformClose()) {
                UndertowLogger.REQUEST_LOGGER.parseRequestTimedOut(connection.getChannel().getPeerAddress());
                IoUtils.safeClose(connection);
            }
        }
    };

    /**
     * Creates new instance of ParseTimeoutSourceConduit.
     *
     * @param channel             Channel which will be closed in case of timeout.
     * @param requestParseTimeout Timeout value. Negative value will indicate that this updated is disabled.
     */
    public ParseTimeoutUpdater(HttpServerConnection channel, int requestParseTimeout) {
        this.connection = channel;
        this.requestParseTimeout = requestParseTimeout;
    }

    /**
     * Needs to be called at least once to start working.
     * <p>
     * This method should be called inside parsing loop. This way Parse Timeout will be kicked off at the first
     * time.
     * </p>
     */
    public void update() {
        if(isEnabled() && hasOpenConnection() && !hasScheduledTimeout()) {
            expireTime = System.currentTimeMillis() + requestParseTimeout + FUZZ_FACTOR;
            handle = connection.getIoThread().executeAfter(timeoutCommand, requestParseTimeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Cancels timeout countdown.
     * <p>
     * Should be called after parsing is complete (to avoid closing connection during other activities).
     * </p>
     */
    public void cancel() {
        if (isEnabled() && hasScheduledTimeout()) {
            // boundary condition - when the other thread is scheduled to execute timeout,
            // the last thing to do is to check if parsing hadn't had finish. We might do this with expireTime
            // (which is volatile).
            expireTime = -1;
            handle.remove();
        }
    }

    private boolean hasScheduledTimeout() {
        return handle != null;
    }

    private boolean isEnabled() {
        return requestParseTimeout > 0;
    }

    /*
     * This is the last check before closing connection. If the parsing completes (even if timeout is already
     * executing) expiryTime set to negative value might cancel it.
     */
    private boolean shouldPerformClose() {
        return expireTime > 0;
    }

    private boolean hasOpenConnection() {
        return connection.isOpen();
    }
}
