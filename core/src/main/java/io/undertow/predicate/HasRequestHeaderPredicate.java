/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.predicate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Returns true if the request headers are true.
 * <p/>
 * If allHeaders is true it will return true if all headers are present
 * otherwise it will return true if a single header is present
 *
 * @author Stuart Douglas
 */
class HasRequestHeaderPredicate implements Predicate {

    private final HttpString[] headers;
    private final boolean allHeaders;

    HasRequestHeaderPredicate(final String[] headers, final boolean allHeaders) {
        this.allHeaders = allHeaders;
        HttpString[] h = new HttpString[headers.length];
        for (int i = 0; i < headers.length; ++i) {
            h[i] = new HttpString(headers[i]);
        }
        this.headers = h;
    }


    @Override
    public boolean resolve(final HttpServerExchange value) {
        if (allHeaders) {
            for (HttpString header : headers) {
                if (!value.getRequestHeaders().contains(header)) {
                    return false;
                }
            }
            return true;
        } else {
            for (HttpString header : headers) {
                if (value.getRequestHeaders().contains(header)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "hasRequestHeaders";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("headers", String[].class);
            params.put("requireAllHeaders", boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("headers");
        }

        @Override
        public String defaultParameter() {
            return "headers";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] headers = (String[]) config.get("headers");
            Boolean all = (Boolean) config.get("requireAllHeaders");
            return new HasRequestHeaderPredicate(headers, all == null ? true : all);
        }
    }
}
