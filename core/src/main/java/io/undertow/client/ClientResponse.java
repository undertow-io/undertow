package io.undertow.client;

import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * A client response. This just contains the parsed response header, the response body
 * can be read from the {@link ClientExchange}.
 *
 * @author Stuart Douglas
 */
public final class ClientResponse extends AbstractAttachable {

    private final HeaderMap responseHeaders;
    private final int responseCode;
    private final String status;
    private final HttpString protocol;

    public ClientResponse(int responseCode, String status, HttpString protocol) {
        this.responseCode = responseCode;
        this.status = status;
        this.protocol = protocol;
        this.responseHeaders = new HeaderMap();
    }

    public ClientResponse(int responseCode, String status, HttpString protocol, HeaderMap headers) {
        this.responseCode = responseCode;
        this.status = status;
        this.protocol = protocol;
        this.responseHeaders = headers;
    }
    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    public HttpString getProtocol() {
        return protocol;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getStatus() {
        return status;
    }
}
