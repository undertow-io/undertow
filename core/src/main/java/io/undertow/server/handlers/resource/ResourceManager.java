package io.undertow.server.handlers.resource;

import java.io.IOException;

/**
 *
 * Representation of a resource manager. A resource manager knows how to obtain
 * a resource for a given path.
 *
 * @author Stuart Douglas
 */
public interface ResourceManager {

    /**
     * Returns a resource for the given path.
     *
     * It is the responsibility of the called to make sure that the path in Canonicalised.
     *
     * @param path The path
     * @return The resource representing the path, or null if no resource was found.
     */
    Resource getResource(final String path) throws IOException;

}
