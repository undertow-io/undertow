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

import io.undertow.UndertowMessages;

/**
 * What is known about a connection, and about the request that was being read on it, at the moment
 * the request was rejected during parsing.
 * <p>
 * Passed to {@link RequestParseErrorListener#onParseError(Throwable, RequestParseErrorContext)}.
 *
 * @see RequestParseErrorListener
 */
public final class RequestParseErrorContext {

    private final ServerConnection connection;
    private final HttpServerExchange exchange;

    RequestParseErrorContext(final ServerConnection connection, final HttpServerExchange exchange) {
        if (connection == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("connection");
        }
        this.connection = connection;
        this.exchange = exchange;
    }

    /**
     * The connection the malformed request arrived on. Never {@code null}.
     * <p>
     * This is the most useful part of the context for attributing a failure to a client:
     * {@link ServerConnection#getPeerAddress()}, {@link ServerConnection#getLocalAddress()},
     * {@link ServerConnection#getTransportProtocol()} and {@link ServerConnection#getId()} are all
     * available. The connection is in the process of being closed on most paths, so do not attempt
     * to write to it.
     *
     * @return the connection, never {@code null}
     */
    public ServerConnection getConnection() {
        return connection;
    }

    /**
     * The exchange that was being populated when parsing failed, if there was one.
     * <p>
     * <strong>This exchange is only partially populated, and must not be used to write a response
     * or to mutate any state.</strong> It is provided for diagnostics only. Depending on how far
     * parsing progressed, and on the protocol, some of the request method, request URI, request
     * path and query parameters may be set, and the rest will be absent. In particular the protocol
     * version, the request scheme and most request headers are frequently not yet set on HTTP/1.1,
     * and there is no response conduit, so writing to the exchange will not behave sensibly.
     * <p>
     * May be {@code null}: some failures are detected before any exchange exists, or after the one
     * in flight has already been released.
     *
     * @return the partially parsed exchange, or {@code null} if none is available
     */
    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public String toString() {
        return "RequestParseErrorContext{peer=" + connection.getPeerAddress()
                + ", protocol=" + connection.getTransportProtocol()
                + ", requestURI=" + (exchange == null ? null : exchange.getRequestURI())
                + "}";
    }
}
