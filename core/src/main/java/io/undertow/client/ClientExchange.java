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


    StreamSinkChannel getRequestChannel();

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

    ClientConnection getConnection();
}
