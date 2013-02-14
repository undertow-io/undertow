package io.undertow.predicate;

/**
 * A predicate.
 *
 * This is mainly uses by handlers as a way to decide if a request should have certain
 * processing applied, based on the given conditions.
 *
 * @author Stuart Douglas
 */
public interface Predicate<T> {

    boolean resolve(final T value);

}
