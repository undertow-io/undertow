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

package io.undertow.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public class MethodPredicate implements Predicate {

    private final HttpString[] methods;

    MethodPredicate(String[] methods) {
        HttpString[] values = new HttpString[methods.length];
        for(int i = 0; i < methods.length; ++i) {
            values[i] = HttpString.tryFromString(methods[i]);
        }
        this.methods = values;
    }


    @Override
    public boolean resolve(final HttpServerExchange value) {
        for(int i =0; i < methods.length; ++i) {
            if(value.getRequestMethod().equals(methods[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if( methods.length == 1 ) {
            return "method( '" + methods[0] + "' )";
        } else {
            return "method( {" + Arrays.asList( methods ).stream().map(s->s.toString()).collect(Collectors.joining(", ")) + "} )";
        }
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "method";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("value", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] methods = (String[]) config.get("value");
            return new MethodPredicate(methods);
        }
    }
}
