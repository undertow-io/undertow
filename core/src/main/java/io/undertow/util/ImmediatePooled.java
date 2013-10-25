package io.undertow.util;

import org.xnio.Pooled;

/**
 * Wrapper that allows you to use a non-pooed item as a pooled value
 *
 * @author Stuart Douglas
 */
public class ImmediatePooled<T> implements Pooled<T> {

    private final T value;

    public ImmediatePooled(T value) {
        this.value = value;
    }

    @Override
    public void discard() {
    }

    @Override
    public void free() {
    }

    @Override
    public T getResource() throws IllegalStateException {
        return value;
    }
}
