/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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

    void setPushHandler(PushCallback pushCallback);

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
