package io.undertow.predicate;

/**
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
        return new SuffixMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if the request path ends with any of the provided suffixs
     */
    public static Predicate suffixs(final String... paths) {
        final SuffixMatchPredicate[] predicates = new SuffixMatchPredicate[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            predicates[i] = new SuffixMatchPredicate(paths[i]);
        }
        return or(predicates);
    }

    /**
     * creates a predicate that returns true if the given relative path starts with the provided prefix
     */
    public static Predicate prefix(final String path) {
        return new PrefixMatchPredicate(path);
    }

    /**
     * creates a predicate that returns true if the relative request path matches any of the provided prefixes
     */
    public static Predicate prefixs(final String... paths) {
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
     *
     * @param headers The headers
     * @return a predicate that returns true if all request headers are present
     */
    public static Predicate hasRequestHeaders(final String ... headers) {
        return new HasRequestHeaderPredicate(headers, true);
    }

    /**
     *
     * @param allHeaders If all headers are required or only a single header
     * @param headers The headers
     * @return a predicate that returns true if request headers are present
     */
    public static Predicate hasRequestHeaders(boolean allHeaders, final String ... headers) {
        return new HasRequestHeaderPredicate(headers, true);
    }

    /**
     * Returns true if the given request header is present and contains one
     * @param header
     * @param values
     * @return
     */
    public static Predicate requestHeaderContains(final String header, final String ... values) {
        return new RequestHeaderContainsPredicate(header, values);
    }

    private Predicates() {

    }
}
