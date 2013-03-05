package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
class OrPredicate<T> implements Predicate<T> {

    private final Predicate<T>[] predicates;

    public OrPredicate(final Predicate<T>... predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean resolve(final T value) {
        for (final Predicate<T> predicate : predicates) {
            if (predicate.resolve(value)) {
                return true;
            }
        }
        return false;
    }
}
