package io.undertow.server.handlers.resource;

import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;

/**
 * Representation of a static resource.
 *
 *
 * @author Stuart Douglas
 */
public interface Resource {

    /**
     *
     * @return The last modified date of this resource, or null if this cannot be determined
     */
    Date getLastModified();

    /**
     *
     * @return The resources etags
     */
    ETag getETag();

    /**
     *
     * @return The name of the resource
     */
    String getName();

    /**
     *
     * @return <code>true</code> if this resource represents a directory
     */
    boolean isDirectory();

    /**
     *
     * @return a list of resources in this directory
     */
    List<Resource> list();

    /**
     * Return the resources content type. In most cases this will simply use the provided
     * mime mappings, however in some cases the resource may have additional information as
     * to the actual content type.
     *
     */
    String getContentType(final MimeMappings mimeMappings);

    /**
     * Serve the resource, and end the exchange when done
     *
     * @param exchange The exchange
     */
    void serve(final HttpServerExchange exchange);

    /**
     *
     * @return The content length, or null if it is unknown
     */
    Long getContentLength();

    Resource getIndexResource(List<String> possible);

    /**
     *
     * @return A string that uniquely identifies this resource
     */
    String getCacheKey();

    /**
     *
     * @return The underlying file that matches the resource. This may return null if the resource does not map to a file
     */
    Path getFile();

    /**
     *
     * @return The URL of the resource
     */
    URL getUrl();
}
