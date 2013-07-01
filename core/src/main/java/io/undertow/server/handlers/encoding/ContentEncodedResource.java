package io.undertow.server.handlers.encoding;

import io.undertow.server.handlers.resource.Resource;

/**
 * A resource that has been pre-compressed
 *
 * @author Stuart Douglas
 */
public class ContentEncodedResource {

    private final Resource resource;
    private final String contentEncoding;

    public ContentEncodedResource(Resource resource, String contentEncoding) {
        this.resource = resource;
        this.contentEncoding = contentEncoding;
    }

    public Resource getResource() {
        return resource;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }
}
