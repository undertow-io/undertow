package io.undertow.server.handlers.resource;

/**
 * An event that is fired when a resource is modified
 *
 * @author Stuart Douglas
 */
public class ResourceChangeEvent {

    private final String resource;
    private final Type type;

    public ResourceChangeEvent(String resource, Type type) {
        this.resource = resource;
        this.type = type;
    }

    public String getResource() {
        return resource;
    }

    public Type getType() {
        return type;
    }

    /**
     * Watched file event types.  More may be added in the future.
     */
    public static enum Type {
        /**
         * A file was added in a directory.
         */
        ADDED,
        /**
         * A file was removed from a directory.
         */
        REMOVED,
        /**
         * A file was modified in a directory.
         */
        MODIFIED,
    }
}
