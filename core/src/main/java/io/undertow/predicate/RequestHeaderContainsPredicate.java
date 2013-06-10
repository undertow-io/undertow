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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

/**
 * Returns true if the request header is present and contains one of the strings to match.
 *
 * @author Stuart Douglas
 */
class RequestHeaderContainsPredicate implements Predicate {

    private final HttpString header;
    private final String[] values;

    RequestHeaderContainsPredicate(final String header, final String[] values) {
        this.header = new HttpString(header);
        this.values = new String[values.length];
        System.arraycopy(values, 0, this.values, 0, values.length);
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        HeaderValues headers = value.getRequestHeaders().get(header);
        if(headers == null) {
            return false;
        }
        for(String header : headers) {
            for(int i = 0; i < values.length; ++i) {
                if(header.contains(values[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "requestHeaderContains";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<String, Class<?>>();
            params.put("value", String[].class);
            params.put("header", String.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<String>();
            params.add("value");
            params.add("header");
            return params;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] values = (String[]) config.get("value");
            String header = (String) config.get("header");
            return new RequestHeaderContainsPredicate(header, values);
        }
    }
}
