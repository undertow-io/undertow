package io.undertow.client;

import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

/**
 * A client request. This class should not be modified once it has been submitted to the {@link ClientConnection}.
 *
 * This class only represents the HTTP header, it does not represent an entity body. If the request needs an entity
 * body then this must be specified by either setting a Content-Length or Transfer-Encoding header, otherwise
 * the client will assume that the body is empty.
 *
 * @author Stuart Douglas
 */
public final class ClientRequest extends AbstractAttachable {

    private final HeaderMap requestHeaders = new HeaderMap();
    private String path = "/";
    private HttpString method = Methods.GET;
    private HttpString protocol = Protocols.HTTP_1_1;

    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    public String getPath() {
        return path;
    }

    public HttpString getMethod() {
        return method;
    }

    public HttpString getProtocol() {
        return protocol;
    }

    public ClientRequest setPath(String path) {
        this.path = path;
        return this;
    }

    public ClientRequest setMethod(HttpString method) {
        this.method = method;
        return this;
    }

    public ClientRequest setProtocol(HttpString protocol) {
        this.protocol = protocol;
        return this;
    }
}
