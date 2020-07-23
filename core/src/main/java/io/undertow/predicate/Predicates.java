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

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;

/**
 * Utility class used for creating predicates
 *
 *
 * @author Stuart Douglas
 */
public class Predicates {

    /**
     * Creates a procedure that returns true if the given ExchangeAttributes are equal.
     * @param attributes to be compared in the predictor.
     * @return A new EqualsPredicate.
     */
    public static Predicate equals(final ExchangeAttribute[] attributes){
        return new EqualsPredicate(attributes);
    }

    /**
     * Creates a predicate that returns true if an only if the given predicates all
     * return true.
     */
    public static  Predicate and(final Predicate... predicates) {
        return new AndPredicate(predicates);
    }

    /**
     * Creates a predicate that returns true if any of the given predicates
     * return true.
     */
    public static  Predicate or(final Predicate... predicates) {
        return new OrPredicate(predicates);
    }

    /**
     * Creates a predicate that returns true if the given predicate returns
     * false.
     */
    public static  Predicate not(final Predicate predicate) {
        return new NotPredicate(predicate);
    }

    /**
     * Creates a predicate that returns true if the given path matches exactly.
     */
    public static Predicate path(final String path) {
        return new PathMatchPredicate(path);
    }

    /**
     * Creates a predicate that returns true if any of the given paths match exactly.
     */
    public static Predicate paths(final String... paths) {
        final PathMatchPredicate[] predicates = new PathMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new PathMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * Creates a predicate that returns true if the request path ends with the provided suffix.
     */
    public static Predicate suffix(final String path) {
        return new PathSuffixPredicate(path);
    }

    /**
     * Creates a predicate that returns true if the request path ends with any of the provided suffixes.
     */
    public static Predicate suffixes(final String... paths) {
        if(paths.length == 1) {
            return suffix(paths[0]);
        }
        final PathSuffixPredicate[] predicates = new PathSuffixPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new PathSuffixPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * Creates a predicate that returns true if the given relative path starts with the provided prefix.
     */
    public static Predicate prefix(final String path) {
        return new PathPrefixPredicate(path);
    }

    /**
     * Creates a predicate that returns true if the relative request path matches any of the provided prefixes.
     */
    public static Predicate prefixes(final String... paths) {
        return new PathPrefixPredicate(paths);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is above a
     * given value.
     *
     * Use {@link #requestLargerThan(long)} instead.
     */
    @Deprecated
    public static Predicate maxContentSize(final long size) {
        return new MaxContentSizePredicate(size);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is below a
     * given value.
     *
     * Use {@link #requestSmallerThan(long)} instead.
     */
    @Deprecated
    public static Predicate minContentSize(final long size) {
        return new MinContentSizePredicate(size);
    }


    /**
     * Predicate that returns true if the Content-Size of a request is smaller than a
     * given size.
     */
    public static Predicate requestSmallerThan(final long size) {
        return new RequestSmallerThanPredicate(size);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is larger than a
     * given size.
     */
    public static Predicate requestLargerThan(final long size) {
        return new RequestLargerThanPredicate(size);
    }

    /**
     * Prediction which always returns true
     */
    public static  Predicate truePredicate() {
        return TruePredicate.instance();
    }

    /**
     * Predicate which always returns false.
     */
    public static  Predicate falsePredicate() {
        return FalsePredicate.instance();
    }

    /**
     * Return a predicate that will return true if the given attribute is not null and not empty.
     *
     * @param attribute The attribute to check whether it exists or not.
     */
    public static Predicate exists(final ExchangeAttribute attribute) {
        return new ExistsPredicate(attribute);
    }

    /**
     * Returns true if the given attribute is present and contains one of the provided value.
     * @param attribute The exchange attribute.
     * @param values The values to check for.
     */
    public static Predicate contains(final ExchangeAttribute attribute, final String ... values) {
        return new ContainsPredicate(attribute, values);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex. A full match is not required
     * @param attribute The exchange attribute to check against.
     * @param pattern The pattern to look for.
     */
    public static Predicate regex(final ExchangeAttribute attribute, final String pattern) {
        return new RegularExpressionPredicate(pattern, attribute);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex.
     * @param requireFullMatch If a full match is required in order to return true.
     * @param attribute The attribute to check against.
     * @param pattern The pattern to look for.
     */
    public static Predicate regex(final ExchangeAttribute attribute, final String pattern, boolean requireFullMatch) {
        return new RegularExpressionPredicate(pattern, attribute, requireFullMatch);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex.
     * @param requireFullMatch If a full match is required in order to return true.
     * @param attribute The attribute to check against.
     * @param pattern The pattern to look for.
     */
    public static Predicate regex(final String attribute, final String pattern, final ClassLoader classLoader, final boolean requireFullMatch) {
        return new RegularExpressionPredicate(pattern, ExchangeAttributes.parser(classLoader).parse(attribute), requireFullMatch);
    }

    /**
     * A predicate that returns true if authentication is required
     *
     * @return A predicate that returns true if authentication is required
     */
    public static Predicate authRequired() {
        return AuthenticationRequiredPredicate.INSTANCE;
    }

    /**
     * parses the predicate string, and returns the result, using the TCCL to load predicate definitions
     * @param predicate The prediate string
     * @return The predicate
     */
    public static Predicate parse(final String predicate) {
        return PredicateParser.parse(predicate, Thread.currentThread().getContextClassLoader());
    }

    /**
     * parses the predicate string, and returns the result
     * @param predicate The prediate string
     * @param classLoader The class loader to load the predicates from
     * @return The predicate
     */
    public static Predicate parse(final String predicate, ClassLoader classLoader) {
        return PredicateParser.parse(predicate, classLoader);
    }

    /**
     *
     * @return A predicate that returns true if the request is secure
     */
    public static Predicate secure() {
        return SecurePredicate.INSTANCE;
    }

    private Predicates() {

    }
}
