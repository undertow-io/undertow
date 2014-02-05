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
     * false
     */
    public static  Predicate not(final Predicate predicate) {
        return new NotPredicate(predicate);
    }

    /**
     * creates a predicate that returns true if the given path matches exactly
     */
    public static Predicate path(final String path) {
        return new PathMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if any of the given paths match exactly
     */
    public static Predicate paths(final String... paths) {
        final PathMatchPredicate[] predicates = new PathMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new PathMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * creates a predicate that returns true if the request path ends with the provided suffix
     */
    public static Predicate suffix(final String path) {
        return new PathSuffixPredicate(path);
    }

    /**
     * creates a predicate that returns true if the request path ends with any of the provided suffixes
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
     * creates a predicate that returns true if the given relative path starts with the provided prefix
     */
    public static Predicate prefix(final String path) {
        return new PathPrefixPredicate(path);
    }

    /**
     * creates a predicate that returns true if the relative request path matches any of the provided prefixes
     */
    public static Predicate prefixes(final String... paths) {
        return new PathPrefixPredicate(paths);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is above a
     * given value.
     *
     * @author Stuart Douglas
     */
    public static Predicate maxContentSize(final long size) {
        return new MaxContentSizePredicate(size);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is below a
     * given value.
     */
    public static Predicate minContentSize(final long size) {
        return new MinContentSizePredicate(size);
    }

    /**
     * predicate that always returns true
     */
    public static  Predicate truePredicate() {
        return TruePredicate.instance();
    }

    /**
     * predicate that always returns false
     */
    public static  Predicate falsePredicate() {
        return FalsePredicate.instance();
    }

    /**
     * Return a predicate that will return true if the given attribute is not null and not empty
     *
     * @param attribute The attribute to check
     */
    public static Predicate exists(final ExchangeAttribute attribute) {
        return new ExistsPredicate(attribute);
    }



    /**
     * Returns true if the given attribute is present and contains one of the provided value
     * @param attribute The exchange attribute
     * @param values The values to check for
     */
    public static Predicate contains(final ExchangeAttribute attribute, final String ... values) {
        return new ContainsPredicate(attribute, values);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex. A full match is not required
     * @param attribute The attribute
     * @param pattern The pattern
     */
    public static Predicate regex(final ExchangeAttribute attribute, final String pattern) {
        return new RegularExpressionPredicate(pattern, attribute);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex.
     * @param requireFullMatch If a full match is required
     * @param attribute The attribute
     * @param pattern The pattern
     */
    public static Predicate regex(final ExchangeAttribute attribute, final String pattern, boolean requireFullMatch) {
        return new RegularExpressionPredicate(pattern, attribute, requireFullMatch);
    }

    /**
     * Creates a predicate that matches the given attribute against a regex.
     * @param requireFullMatch If a full match is required
     * @param attribute The attribute
     * @param pattern The pattern
     */
    public static Predicate regex(final String attribute, final String pattern, final ClassLoader classLoader, final boolean requireFullMatch) {
        return new RegularExpressionPredicate(pattern, ExchangeAttributes.parser(classLoader).parse(attribute), requireFullMatch);
    }

    private Predicates() {

    }
}
