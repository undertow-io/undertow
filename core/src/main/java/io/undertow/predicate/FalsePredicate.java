package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
public class FalsePredicate<T> implements Predicate<T> {

    public static final FalsePredicate INSTANCE = new FalsePredicate();

    public static <T> FalsePredicate<T> instance() {
        return INSTANCE;
    }

    @Override
    public boolean resolve(final T value) {
        return false;
    }
}
