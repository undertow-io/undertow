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

import io.undertow.server.HttpServerExchange;

public interface RateLimiter<K> {

    /**
     * Return time window duration(seconds). After this time elapses, entries are reset.
     *
     * @return
     */
    int getWindowDuration();

    /**
     * Return time in seconds to window slide. Depending on implementation, parameter may be relevant( if time window is
     * personalized for entry).
     *
     * @param ipAddress
     * @return
     */
    int timeToWindowSlide(K key);

    /**
     * Return
     *
     * @return
     */
    int getRequestLimit();

    /**
     * Increment entries for exchange
     *
     * @param ipAddress
     * @return
     */
    int increment(K key);

    /**
     *
     * @param exchange
     * @return Positive value capped at limit or -1 if there is no entry. Positive value correspond to currently acumulated
     *         hits.
     */
    int current(K key);

    /**
     * Utility method to generate key from exchange.
     */
    K generateKey(HttpServerExchange e);

    /**
     * ID, this identifies limiter type in HTTP header
     *
     * @return
     */
    String getLimiterID();

    /**
     * Return limit type.
     * @return
     */
    RateLimitUnit getUnit();

    default String getPolicy() {
        // Format: https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/ -> 3. RateLimit-Policy Field
        return "\"" + getLimiterID() + "\";q=" + getRequestLimit() + ";qu=" + getUnit() + ";w=" + getWindowDuration();
    }

    default String getRemainingQuota(K exchange) {
        // Format: https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/ -> 4. RateLimit Field
        final int current = current(exchange);
        int r;
        if(current == -1) {
            r = getRequestLimit();
        } else if(current >= getRequestLimit()) {
            r= 0;
        } else {
            r = getRequestLimit() - current;
        }
        final int t = timeToWindowSlide(exchange);
        return "\"" + getLimiterID() + "\";r=" + r + ";t=" + t;
    }

}
