package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
public class FalsePredicate<T> implements Predicate<T> {

    @Override
    public boolean resolve(final T value) {
        return false;
    }
}
