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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.xnio.XnioExecutor;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.WorkerUtils;

/**
 * This is bit shift implementation of sliding window. Both rate and duration are converted to next 2^n in order to simply
 * bitshift values, rather than perform 10 based math and convert back into 2 base representation. This implementation of
 * {@link RateLimiter} has single, common window for all entries. For which keys are computed with bit shifting ops. This is not
 * precise, but has very low performance impact.
 */
public class BitShiftSingleWindowRateLimiter implements RateLimiter<HttpServerExchange> {
    // Single window to make it less resource hungry and simpler for first iteration.
    private int windowDuration; // duration in seconds.
    private int requestLimit;
    private volatile XnioExecutor.Key evictionKey;
    // numbers of bits to shift. This will be used to determine prefix for 'requestCounter'; Its based on duration next power of
    // 2;
    private int bitsToShift;
    // This map will store entries under key == prefix-IPAddress. where prefix is
    // "major" part of timestamp
    // Prefix math:
    // 10000 --> 10
    // 10001 --> 10
    // Next: 10+1 --> 11000
    // In other words as long as bitsToShift wont cover ++, prefix will remain constant and allow fast and predictable
    // calculation of key.
    // Any key that does not start with prefix is outdated(window slid over it).
    // NOTE: this can be improved with Long as key and having upper store ~tstamp and lower having pure byte[] representation
    // of IP. Rest of logic would remain the same as for String key.
    // TODO: sanity check, this is IO thread, so no need for concurrent map and AtomicInteger ?
    private ConcurrentHashMap<String, AtomicInteger> requestCounter = new ConcurrentHashMap<String, AtomicInteger>(5000);
    private static final String PREFIX_SEPARATOR = "-";
    private static final int TICK_BORDER = Integer.MAX_VALUE - 1;
    /**
     * Create bit shift limiter. Implementation will adjust values of bot windowDuration and requestLimit, to adjust to next ^2.
     * Meaning duration of 33, will become 64. requestLimit will be adjusted to reflect this "stretch".
     *
     * @param windowDuration
     * @param requestLimit
     */
    public BitShiftSingleWindowRateLimiter(final int windowDuration, final int requestLimit) {
        assert windowDuration > 0;
        assert requestLimit > 0;
        this.bitsToShift = determineBitShiftForDuration(windowDuration);
        this.windowDuration = Math.toIntExact(1L << this.bitsToShift) / 1000;
        // need to adjust requests, based on difference between bitshift duration and one that was passed here.
        // This is done to cover cases when nextP2 is not close to duration, for instance original duration 33s, will
        // switch to ~64, to have it work properly, we need to adjust limit as well.
        this.requestLimit = (int)(((float)this.windowDuration/windowDuration) * requestLimit);
    }

    @Override
    public int getWindowDuration() {
        return this.windowDuration;
    }

    @Override
    public int getRequestLimit() {
        return this.requestLimit;
    }

    @Override
    public int timeToWindowSlide(final HttpServerExchange exchange) {
        // window is common so we ignore parameter.
        // nextPrefix->miliseconds-currentMilis/1000->s;
        final long currentMilis = System.currentTimeMillis();
        return Math.toIntExact((((generatePrefix(currentMilis) + 1)<<bitsToShift) - currentMilis)/1000);
    }

    @Override
    public int increment(final HttpServerExchange exchange) {
        evictionCheck(exchange);

        final String ipAddress = getIPAddress(exchange, true);
        final String key = generateKey(ipAddress);
        AtomicInteger ai = requestCounter.computeIfAbsent(key, v -> new AtomicInteger());
        return ai.accumulateAndGet(1, (value, upTick) -> {
            // JIC
            if (value < TICK_BORDER) {
                return value + upTick; //currentValue + 1(upTick)
            } else {
                return Integer.MAX_VALUE;
            }
        });
    }

    @Override
    public int current(final HttpServerExchange exchange) {
        final String ipAddress = getIPAddress(exchange, true);
        final String key = generateKey(ipAddress);
        AtomicInteger entry = requestCounter.get(key);
        if(entry != null) {
            return entry.get();
        } else {
            return -1;
        }
    }

    private String getIPAddress(final HttpServerExchange exchange, final boolean warn) {
        final InetSocketAddress sourceAddress = exchange.getSourceAddress();
        InetAddress address = sourceAddress.getAddress();
        if (address == null) {
            // this can happen when we have an unresolved X-forwarded-for address
            // in this case we just return the IP of the balancer
            //TODO: this needs impr
            address = ((InetSocketAddress) exchange.getConnection().getPeerAddress()).getAddress();
            if(warn) {
                UndertowLogger.REQUEST_LOGGER.rateLimitFailedToGetProperAddress(exchange.getRequestURI(), address.getHostAddress());
            }
        }
        return address.getHostAddress();
    }

    @Override
    public String getLimiterID() {
        return "bit-shift-window";
    }

    @Override
    public RateLimitUnit getUnit() {
        return RateLimitUnit.REQUEST;
    }

    @Override
    public HttpServerExchange generateKey(HttpServerExchange e) {
        return e;
    }

    private void evictionCheck(HttpServerExchange exchange) {
        // we need to parasite on IO threads for eviction.
        XnioExecutor.Key key = this.evictionKey;
        if (key == null) {
            this.evictionKey = WorkerUtils.executeAfter(exchange.getIoThread(), new Runnable() {
                @Override
                public void run() {
                    evictionKey = null;
                    evictOldWindow();
                }
            }, this.windowDuration, TimeUnit.SECONDS);
        }
    }

    private void evictOldWindow() {
        // evict entries that are not 'current' or 'current+1' - just in case eviction starts in one window and finish in next.
        // in such case 'current' will become stale already and will be evicted on next call. Thats fine.
        // prefix + 1 will translate into bitshift ++
        final long currentPrefix = generatePrefix();
        final String current = String.valueOf(currentPrefix);
        final String next = String.valueOf(currentPrefix + 1);

        ConcurrentHashMap.KeySetView<String, AtomicInteger> keys = requestCounter.keySet();
        // remove obsolete keys
        keys.removeIf(k -> !k.startsWith(current) && !k.startsWith(next));

    }

    private String generateKey(String ipAddress) {
        return generatePrefix() + PREFIX_SEPARATOR + ipAddress;
    }

    private long generatePrefix(long timeMilis) {
        return timeMilis >> this.bitsToShift;
    }

    private long generatePrefix() {
        return generatePrefix(System.currentTimeMillis());
    }

    private int nextPowerOf2(final int v) {
        // this will return closest one bit, for 19, it will be 16. << 1 to get next highest
        final int higherOneBitValue = Integer.highestOneBit(v);
        if (v == higherOneBitValue) {
            return higherOneBitValue;
        } else {
            return higherOneBitValue << 1;
        }
    }

    private int determineBitShiftForDuration(final int duration) {
        // duration to milliseconds.
        final int nextP2 = nextPowerOf2(duration * 1000);
        // since its pure next power of 2, it has leading 1 and trailing zeros, which are equal to bit shift
        return Integer.numberOfTrailingZeros(nextP2);
    }

}
