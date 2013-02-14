package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
public class TruePredicate<T> implements Predicate<T> {

    public static TruePredicate INSTANCE = new TruePredicate();

    public static <T> TruePredicate<T> instance() {
        return INSTANCE;
    }

    @Override
    public boolean resolve(final T value) {
        return true;
    }
}
