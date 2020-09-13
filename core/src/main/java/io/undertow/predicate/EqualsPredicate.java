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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HttpServerExchange;

/**
 * Returns true if all the provided arguments are equal to each other
 *
 * @author Stuart Douglas
 */
public class EqualsPredicate implements Predicate {

    private final ExchangeAttribute[] attributes;

    EqualsPredicate(final ExchangeAttribute[] attributes) {
        this.attributes = attributes;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        if(attributes.length < 2) {
            return true;
        }
        String first = attributes[0].readAttribute(value);
        for(int i = 1; i < attributes.length; ++i) {
            String current = attributes[i].readAttribute(value);
            if(first == null) {
                if(current != null) {
                    return false;
                }
            } else if(current == null) {
                return false;
            } else if(!first.equals(current)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "equals( {" +  Arrays.asList( attributes ).stream().map(a->a.toString()).collect(Collectors.joining(", ")) + "} )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "equals";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("value", ExchangeAttribute[].class);
            return params;
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
            ExchangeAttribute[] value = (ExchangeAttribute[]) config.get("value");
            return new EqualsPredicate(value);
        }
    }
}
