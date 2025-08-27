/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers.ratelimit;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * Rate limiter per IP. Depending on implementation of limiting mechanism it can differ slightly on how it works, but baseline
 * will be the same. Once limit is hit, this handler will reject incomming traffic with specified code and reply. Once engine
 * determines that its fine to pass, it will invoke next handler in chain.
 */
public class RateLimitingHandler implements HttpHandler {
    // defaults
    /**
     * Default status code to return if requests per duration is exceeded.
     */
    public static final int DEFAULT_STATUS_CODE = StatusCodes.TOO_MANY_REQUESTS;

    /**
     * Default status message to return if requests per duration is exceeded.
     */
    public static final String DEFAULT_STATUS_MESSAGE = StatusCodes.TOO_MANY_REQUESTS_STRING;

    /**
     * Default status message to return if requests per duration is exceeded.
     */
    public static final Boolean DEFAULT_ENFORCED = Boolean.TRUE;

    /**
     * Default value, indicating that handler wont send back headers to indicate state of policy for particular entry
     */
    public static final boolean DEFAULT_SIGNAL_LIMITS = false;

    /**
     * Name of the rate limit policy header field defined in
     * <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers">RateLimit header fields for HTTP
     * (draft) # 3. RateLimit-Policy Field</a>.
     */
    public static final String HEADER_NAME_RATE_LIMIT_POLICY = "RateLimit-Policy";

    /**
     * Name of the rate limit remaining quota header field defined in
     * <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers">RateLimit header fields for HTTP
     * (draft) # 4. RateLimit Field</a>.
     */
    public static final String HEADER_NAME_RATE_LIMIT = "RateLimit";

    private final HttpHandler nextHandler;
    private RateLimiter<Object> rateLimiter;
    private String statusMessage = DEFAULT_STATUS_MESSAGE;
    private int statucCode = DEFAULT_STATUS_CODE;
    private boolean enforced = DEFAULT_ENFORCED;
    private boolean signalLimits = DEFAULT_SIGNAL_LIMITS;

    public RateLimitingHandler(final HttpHandler nextHandler, final RateLimiter<Object> rateLimiter) {
        super();
        assert nextHandler != null;
        assert rateLimiter != null;
        this.nextHandler = nextHandler;
        this.rateLimiter = rateLimiter;
    }

    public RateLimitingHandler(final HttpHandler nextHandler, final RateLimiter<Object> rateLimiter, final String statusMessage,
            final int code, final boolean enforced, final boolean signal) {
        this(nextHandler, rateLimiter);
        this.statucCode = code;
        this.statusMessage = statusMessage;
        this.enforced = enforced;
        this.signalLimits = signal;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // TODO: do we need some ID in logs in case there is more rate limiters set up based on something?
        // TODO: add proxy unwinding here? getSourceAddress might take care of this
        final InetSocketAddress sourceAddress = exchange.getSourceAddress();
        InetAddress address = sourceAddress.getAddress();
        if (address == null) {
            // this can happen when we have an unresolved X-forwarded-for address
            // in this case we just return the IP of the balancer
            address = ((InetSocketAddress) exchange.getConnection().getPeerAddress()).getAddress();
        }
        final String ipAddress = address.getHostAddress();
        final Object key = rateLimiter.generateKey(exchange);
        final int currentRequestCount = rateLimiter.increment(key);

        //this has to be done before handling limit, in case it went over
        if (this.signalLimits) {
            final HeaderMap responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add(HttpString.tryFromString(HEADER_NAME_RATE_LIMIT_POLICY), rateLimiter.getPolicy());
            if (this.enforced) {
                responseHeaders.add(HttpString.tryFromString(HEADER_NAME_RATE_LIMIT), rateLimiter.getRemainingQuota(key));
            }
        }

        if (currentRequestCount > rateLimiter.getRequestLimit()) {
            UndertowLogger.REQUEST_LOGGER.exchangeExceedsRequestRateLimit(exchange.getRequestURI(), ipAddress,
                    rateLimiter.getRequestLimit(), rateLimiter.getWindowDuration(), rateLimiter.timeToWindowSlide(key), this.enforced);
            if (this.enforced) {
                exchange.setStatusCode(this.statucCode);
                exchange.setReasonPhrase(this.statusMessage);
                exchange.endExchange();
                return;
            }
        }
        this.nextHandler.handleRequest(exchange);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public int getStatucCode() {
        return statucCode;
    }

    public void setStatucCode(int statucCode) {
        this.statucCode = statucCode;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public void setEnforced(boolean enforced) {
        this.enforced = enforced;
    }

    public boolean isSignalLimits() {
        return signalLimits;
    }

    public void setSignalLimits(boolean signalLimits) {
        this.signalLimits = signalLimits;
    }

}
