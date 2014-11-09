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

package io.undertow.server.protocol;

import io.undertow.UndertowLogger;
import io.undertow.server.ServerConnection;
import org.xnio.IoUtils;
import org.xnio.XnioExecutor;

import java.util.concurrent.TimeUnit;

/**
 * Wrapper for parse timeout.
 *
 * @author Sebastian Laskawiec
 * @see io.undertow.UndertowOptions#REQUEST_PARSE_TIMEOUT
 */
public final class ParseTimeoutUpdater implements Runnable, ServerConnection.CloseListener {

    private final ServerConnection connection;
    private final long requestParseTimeout;
    private final long requestIdleTimeout;
    private volatile XnioExecutor.Key handle;
    private volatile long expireTime = -1;
    private boolean parsing = false;

    //we add 50ms to the timeout to make sure the underlying channel has actually timed out
    private static final int FUZZ_FACTOR = 50;


    /**
     * Creates new instance of ParseTimeoutSourceConduit.
     *  @param channel             Channel which will be closed in case of timeout.
     * @param requestParseTimeout Timeout value. Negative value will indicate that this updated is disabled.
     * @param requestIdleTimeout
     */
    public ParseTimeoutUpdater(ServerConnection channel, long requestParseTimeout, long requestIdleTimeout) {
        this.connection = channel;
        this.requestParseTimeout = requestParseTimeout;
        this.requestIdleTimeout = requestIdleTimeout;
    }

    /**
     * Called when the connection goes idle
     */
    public void connectionIdle() {
        parsing = false;
        handleSchedule(requestIdleTimeout);
    }

    private void handleSchedule(long timeout) {
        //no current timeout, clear the expire time
        if(timeout == -1) {
            this.expireTime = -1;
            return;
        }
        //calculate the new expire time
        long newExpireTime = System.currentTimeMillis() + timeout;
        long oldExpireTime = this.expireTime;
        this.expireTime = newExpireTime;
        //if the new one is less than the current one we need to schedule a new timer, so cancel the old one
        if(newExpireTime < oldExpireTime) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        if(handle == null) {
            handle = connection.getIoThread().executeAfter(this, timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Called when a request is received, however it is not parsed in a single read() call. This starts a timer,
     * and if the request is not parsed within this time then the connection is closed.
     *
     */
    public void failedParse() {
        if(!parsing) {
            parsing = true;
            handleSchedule(requestParseTimeout);
        }
    }

    /**
     * Cancels timeout countdown.
     * <p>
     * Should be called after parsing is complete (to avoid closing connection during other activities).
     * </p>
     */
    public void requestStarted() {
        expireTime = -1;
        parsing = false;
    }

    @Override
    public void run() {
        if(!connection.isOpen()) {
            return;
        }
        handle = null;
        if (expireTime > 0) { //timeout is not active
            long now = System.currentTimeMillis();
            if(expireTime > now) {
                handle = connection.getIoThread().executeAfter(this, (expireTime - now) + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
            } else {
                UndertowLogger.REQUEST_LOGGER.parseRequestTimedOut(connection.getPeerAddress());
                IoUtils.safeClose(connection);
            }
        }
    }

    @Override
    public void closed(ServerConnection connection) {
        if(handle != null) {
            handle.remove();
            handle = null;
        }
    }
}
