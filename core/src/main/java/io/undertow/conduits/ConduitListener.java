package io.undertow.conduits;

import java.util.EventListener;

import org.xnio.conduits.Conduit;

/**
 * @author Stuart Douglas
 */
public interface ConduitListener<T extends Conduit> extends EventListener {

    /**
     * Handle the event on this conduit.
     *
     * @param channel the channel event
     */
    void handleEvent(T channel);
}
