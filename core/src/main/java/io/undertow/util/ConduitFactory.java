package io.undertow.util;

import org.xnio.conduits.Conduit;

/**
 * @author Stuart Douglas
 */
public interface ConduitFactory<C extends Conduit> {

    /**
     * Create the channel instance.
     *
     * @return the channel instance
     */
    C create();
}
