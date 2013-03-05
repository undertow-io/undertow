package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
class TruePredicate<T> implements Predicate<T> {

    public static final TruePredicate INSTANCE = new TruePredicate();

    public static <T> TruePredicate<T> instance() {
        return INSTANCE;
    }

    @Override
    public boolean resolve(final T value) {
        return true;
    }
}
