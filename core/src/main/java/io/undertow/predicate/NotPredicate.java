package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
class NotPredicate<T> implements Predicate<T>{

    private final Predicate<T> predicate;

    public NotPredicate(final Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean resolve(final T value) {
        return !predicate.resolve(value);
    }
}
