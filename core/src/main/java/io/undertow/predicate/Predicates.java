package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class Predicates {

    /**
     * Creates a predicate that returns true if an only if the given predicates all
     * return true.
     */
    public static <T> Predicate<T> and(final Predicate<T>... predicates) {
        return new AndPredicate<T>(predicates);
    }

    /**
     * Creates a predicate that returns true if any of the given predicates
     * return true.
     */
    public static <T> Predicate<T> or(final Predicate<T>... predicates) {
        return new OrPredicate<T>(predicates);
    }

    /**
     * Creates a predicate that returns true if the given predicate returns
     * false
     */
    public static <T> Predicate<T> not(final Predicate<T> predicate) {
        return new NotPredicate<T>(predicate);
    }

    /**
     * creates a predicate that returns true if the given path matches exactly
     */
    public static Predicate<HttpServerExchange> path(final String path) {
        return new PathMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if any of the given paths match exactly
     */
    public static Predicate<HttpServerExchange> paths(final String... paths) {
        final PathMatchPredicate[] predicates = new PathMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new PathMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * creates a predicate that returns true if the request path ends with the provided suffix
     */
    public static Predicate<HttpServerExchange> suffix(final String path) {
        return new SuffixMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if the request path ends with any of the provided suffixs
     */
    public static Predicate<HttpServerExchange> suffixs(final String... paths) {
        final SuffixMatchPredicate[] predicates = new SuffixMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new SuffixMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * creates a predicate that returns true if the given relative path starts with the provided prefix
     */
    public static Predicate<HttpServerExchange> prefix(final String path) {
        return new PrefixMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if the relative request path matches any of the provided prefixes
     */
    public static Predicate<HttpServerExchange> prefixs(final String... paths) {
        final PrefixMatchPredicate[] predicates = new PrefixMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new PrefixMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is above a
     * given value.
     *
     * @author Stuart Douglas
     */
    public static Predicate<HttpServerExchange> maxContentSize(final long size) {
        return new MaxContentSizePredicate(size);
    }

    /**
     * Predicate that returns true if the Content-Size of a request is below a
     * given value.
     */
    public static Predicate<HttpServerExchange> minContentSize(final long size) {
        return new MinContentSizePredicate(size);
    }

    /**
     * predicate that always returns true
     */
    public static <T> Predicate<T> truePredicate() {
        return TruePredicate.instance();
    }

    /**
     * predicate that always returns false
     */
    public static <T> Predicate<T> falsePredicate() {
        return FalsePredicate.instance();
    }

    private Predicates() {

    }
}
