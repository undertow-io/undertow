package io.undertow.server.handlers.resource;

import java.util.Collection;

/**
 * Listener that is invoked when a resource changes.
 *
* @author Stuart Douglas
*/
public interface ResourceChangeListener {

    /**
     * callback that is invoked when resources change.
     * @param changes The collection of changes
     */
    void handleChanges(final Collection<ResourceChangeEvent> changes);

}
