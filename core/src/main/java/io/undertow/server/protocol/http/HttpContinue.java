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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import io.undertow.UndertowMessages;
import io.undertow.connector.IoResult;
import io.undertow.connector.IoSink;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;

/**
 * Class that provides support for dealing with HTTP 100 (Continue) responses.
 * <p>
 * Note that if a client is pipelining some requests and sending continue for others this
 * could cause problems if the pipelining buffer is enabled.
 *
 * @author Stuart Douglas
 */
public class HttpContinue {

    private static final Set<HttpString> COMPATIBLE_PROTOCOLS;

    static {
        Set<HttpString> compat = new HashSet<>();
        compat.add(Protocols.HTTP_1_1);
        compat.add(Protocols.HTTP_2_0);
        COMPATIBLE_PROTOCOLS = Collections.unmodifiableSet(compat);
    }

    public static final String CONTINUE = "100-continue";

    private static final AttachmentKey<Boolean> ALREADY_SENT = AttachmentKey.create(Boolean.class);

    /**
     * Returns true if this exchange requires the server to send a 100 (Continue) response.
     *
     * @param exchange The exchange
     * @return <code>true</code> if the server needs to send a continue response
     */
    public static boolean requiresContinueResponse(final HttpServerExchange exchange) {
        if (!COMPATIBLE_PROTOCOLS.contains(exchange.getProtocol()) || exchange.isResponseStarted() || !exchange.getConnection().isContinueResponseSupported() || exchange.getAttachment(ALREADY_SENT) != null) {
            return false;
        }

        HeaderMap requestHeaders = exchange.getRequestHeaders();
        return requiresContinueResponse(requestHeaders);
    }

    public static boolean requiresContinueResponse(HeaderMap requestHeaders) {
        List<String> expect = requestHeaders.get(Headers.EXPECT);
        if (expect != null) {
            for (String header : expect) {
                if (header.equalsIgnoreCase(CONTINUE)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Sends a continuation using async IO, and calls back when it is complete.
     *
     * @param exchange The exchange
     * @param callback The completion callback
     */
    public static void sendContinueResponse(final HttpServerExchange exchange, final IoCallback callback) {
        if (!exchange.isResponseChannelAvailable()) {
            callback.onException(exchange, null, UndertowMessages.MESSAGES.cannotSendContinueResponse());
            return;
        }
        internalSendContinueResponse(exchange, callback);
    }

    /**
     * Creates a response sender that can be used to send a HTTP 100-continue response.
     *
     * @param exchange The exchange
     * @return The response sender
     */
    public static IoResult<Void> createResponseSender(final HttpServerExchange exchange) throws IOException {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.cannotSendContinueResponse();
        }
        if (exchange.getAttachment(ALREADY_SENT) != null) {
            return new IoResult<Void>() {
                @Override
                public Void get() throws IOException {
                    return null;
                }

                @Override
                public void addNotifier(BiConsumer<Void, IOException> notifier) {
                    notifier.accept(null, null);
                }
            };
        }

        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        exchange.putAttachment(ALREADY_SENT, true);
        newExchange.setStatusCode(StatusCodes.CONTINUE);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        final IoSink responseChannel = newExchange.getResponseChannel();
        return responseChannel.close();
    }

    /**
     * Marks a continue response as already having been sent. In general this should only be used
     * by low level handlers than need fine grained control over the continue response.
     *
     * @param exchange The exchange
     */
    public static void markContinueResponseSent(HttpServerExchange exchange) {
        exchange.putAttachment(ALREADY_SENT, true);
    }

    /**
     * Sends a continue response using blocking IO
     *
     * @param exchange The exchange
     */
    public static void sendContinueResponseBlocking(final HttpServerExchange exchange) throws IOException {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.cannotSendContinueResponse();
        }
        if (exchange.getAttachment(ALREADY_SENT) != null) {
            return;
        }
        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        exchange.putAttachment(ALREADY_SENT, true);
        newExchange.setStatusCode(StatusCodes.CONTINUE);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        newExchange.startBlocking();
        newExchange.getOutputStream().close();
        newExchange.getInputStream().close();
    }

    /**
     * Sets a 417 response code and ends the exchange.
     *
     * @param exchange The exchange to reject
     */
    public static void rejectExchange(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.EXPECTATION_FAILED);
        exchange.setPersistent(false);
        exchange.endExchange();
    }


    private static void internalSendContinueResponse(final HttpServerExchange exchange, final IoCallback callback) {
        if (exchange.getAttachment(ALREADY_SENT) != null) {
            callback.onComplete(exchange, null);
            return;
        }
        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        exchange.putAttachment(ALREADY_SENT, true);
        newExchange.setStatusCode(StatusCodes.CONTINUE);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        final IoSink responseChannel = newExchange.getResponseChannel();
        newExchange.getResponseSender().close(callback);
    }


}
