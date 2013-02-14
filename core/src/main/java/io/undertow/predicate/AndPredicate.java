package io.undertow.predicate;

/**
 * @author Stuart Douglas
 */
public class AndPredicate<T> implements Predicate<T>{

    private final Predicate<T>[] predicates;

    public AndPredicate(final Predicate<T> ... predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean resolve(final T value) {
        for(final Predicate<T> predicate : predicates) {
            if(!predicate.resolve(value)) {
                return false;
            }
        }
        return true;
    }
}
