package io.undertow.util;

import org.xnio.conduits.Conduit;

/**
 * @author Stuart Douglas
 */
public class ImmediateConduitFactory<T extends Conduit> implements ConduitFactory<T> {

    private final T value;

    public ImmediateConduitFactory(final T value) {
        this.value = value;
    }

    @Override
    public T create() {
        return value;
    }
}
