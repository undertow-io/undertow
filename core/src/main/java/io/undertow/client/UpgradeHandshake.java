package io.undertow.client;

import java.io.IOException;

/**
 * Class that represents the client side of an upgrade request. This class is responsible
 * for creating the http request, and validating the response.
 *
 *
 *
 * @author Stuart Douglas
 */
public interface UpgradeHandshake {

    HttpClientRequest createRequest(HttpClientConnection connection);

    void validateResponse(final HttpClientConnection connection, final HttpClientResponse response) throws IOException;


}
