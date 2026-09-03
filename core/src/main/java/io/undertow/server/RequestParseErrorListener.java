/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

/**
 * A listener that is notified when an incoming request is rejected while it is being parsed,
 * before it can be dispatched to the handler chain.
 * <p>
 * Requests that fail to parse never reach an {@link HttpHandler}, so an application has no
 * ordinary way of observing them. Undertow logs such failures at {@code DEBUG} on the
 * {@code io.undertow.request.io} category deliberately, because the traffic that causes them is
 * attacker controlled and easy to use to flood a log. This listener exists so that an application
 * can route those failures to its own logging or monitoring system instead.
 * <p>
 * The listener is purely an observer. It cannot alter the response, and it cannot prevent the
 * connection from being closed; Undertow responds exactly as it would if no listener were
 * registered. Any exception thrown by {@link #onParseError} is caught and logged, so a faulty
 * listener cannot break the connector.
 * <p>
 * Implementations are invoked on an I/O thread and must not block.
 *
 * @see RequestParseErrorContext
 * @see io.undertow.Undertow.Builder#setRequestParseErrorListener(RequestParseErrorListener)
 */
public interface RequestParseErrorListener {

    /**
     * A listener that does nothing. Used as the default when none has been configured.
     */
    RequestParseErrorListener NO_OP = new RequestParseErrorListener() {
        @Override
        public void onParseError(Throwable error, RequestParseErrorContext context) {
        }

        @Override
        public String toString() {
            return "RequestParseErrorListener.NO_OP";
        }
    };

    /**
     * Invoked when a request could not be parsed.
     *
     * @param error   the failure that caused the request to be rejected, never {@code null}
     * @param context what is known about the connection and the partially parsed request,
     *                never {@code null}
     */
    void onParseError(Throwable error, RequestParseErrorContext context);
}
