package io.undertow.client;

import io.undertow.util.Attachable;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public interface ClientExchange extends Attachable {

    void setResponseListener(final ClientCallback<ClientExchange> responseListener);

    void setContinueHandler(final ContinueNotification continueHandler);

    /**
     * Returns the request channel that can be used to send data to the server.
     *
     * @return The request channel
     */
    StreamSinkChannel getRequestChannel();

    /**
     * Returns the response channel that can be used to read data from the target server.
     *
     * @return The response channel
     */
    StreamSourceChannel getResponseChannel();

    ClientRequest getRequest();

    /**
     *
     * @return The client response, or null if it has not been received yet
     */
    ClientResponse getResponse();

    /**
     *
     * @return the result of a HTTP 100-continue response
     */
    ClientResponse getContinueResponse();

    /**
     *
     * @return The underlying connection
     */
    ClientConnection getConnection();
}
