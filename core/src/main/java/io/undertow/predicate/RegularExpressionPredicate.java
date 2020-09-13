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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.UndertowLogger;

/**
 * A predicate that does a regex match against an exchange.
 * <p>
 * <p>
 * By default this match is done against the relative URI, however it is possible to set it to match against other
 * exchange attributes.
 *
 * @author Stuart Douglas
 */
public class RegularExpressionPredicate implements Predicate {

    private final Pattern pattern;
    private final ExchangeAttribute matchAttribute;
    private final boolean requireFullMatch;
    private static final boolean traceEnabled;

    static {
        traceEnabled = UndertowLogger.PREDICATE_LOGGER.isTraceEnabled();
    }

    public RegularExpressionPredicate(final String regex, final ExchangeAttribute matchAttribute, final boolean requireFullMatch, boolean caseSensitive) {
        this.requireFullMatch = requireFullMatch;
        pattern = Pattern.compile(regex, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        this.matchAttribute = matchAttribute;
    }

    public RegularExpressionPredicate(final String regex, final ExchangeAttribute matchAttribute, final boolean requireFullMatch) {
        this(regex, matchAttribute, requireFullMatch, true);
    }

    public RegularExpressionPredicate(final String regex, final ExchangeAttribute matchAttribute) {
        this(regex, matchAttribute, false);
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        String input = matchAttribute.readAttribute(value);
        if(input == null) {
            return false;
        }
        Matcher matcher = pattern.matcher(input);
        final boolean matches;
        if (requireFullMatch) {
            matches = matcher.matches();
        } else {
            matches = matcher.find();
        }
        if (traceEnabled) {
            UndertowLogger.PREDICATE_LOGGER.tracef("Regex pattern [%s] %s input [%s] for %s.", pattern.toString(), (matches ? "MATCHES" : "DOES NOT MATCH" ), input, value);
        }
        if (matches) {
            Map<String, Object> context = value.getAttachment(PREDICATE_CONTEXT);
            if(context == null) {
                value.putAttachment(PREDICATE_CONTEXT, context = new TreeMap<>());
            }
            int count = matcher.groupCount();
            for (int i = 0; i <= count; ++i) {
                if (traceEnabled) {
                    UndertowLogger.PREDICATE_LOGGER.tracef("Storing regex match group [%s] as [%s] for %s.", i, matcher.group(i), value);
                }
                context.put(Integer.toString(i), matcher.group(i));
            }
        }
        return matches;
    }

    @Override
    public String toString() {
        return "regex( pattern='" + pattern.toString() +  "', value='" + matchAttribute.toString() + "', full-match='" + Boolean.valueOf( this.requireFullMatch ) + "', case-sensitive='" + Boolean.valueOf( ( pattern.flags() & Pattern.CASE_INSENSITIVE ) == Pattern.CASE_INSENSITIVE ) + "' )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "regex";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("pattern", String.class);
            params.put("value", ExchangeAttribute.class);
            params.put("full-match", Boolean.class);
            params.put("case-sensitive", Boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<>();
            params.add("pattern");
            return params;
        }

        @Override
        public String defaultParameter() {
            return "pattern";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            if(value == null) {
                value = ExchangeAttributes.relativePath();
            }
            Boolean fullMatch = (Boolean) config.get("full-match");
            Boolean caseSensitive = (Boolean) config.get("case-sensitive");
            String pattern = (String) config.get("pattern");
            return new RegularExpressionPredicate(pattern, value, fullMatch == null ? false : fullMatch, caseSensitive == null ? true : caseSensitive);
        }
    }
}
