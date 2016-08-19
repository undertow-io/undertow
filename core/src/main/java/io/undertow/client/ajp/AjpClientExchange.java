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

package io.undertow.client.ajp;

import io.undertow.channels.DetachableStreamSinkChannel;
import io.undertow.channels.DetachableStreamSourceChannel;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ContinueNotification;
import io.undertow.client.PushCallback;
import io.undertow.protocols.ajp.AjpClientRequestClientStreamSinkChannel;
import io.undertow.protocols.ajp.AjpClientResponseStreamSourceChannel;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Headers;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;

import static org.xnio.Bits.anyAreSet;

/**
 * @author Stuart Douglas
 */
class AjpClientExchange extends AbstractAttachable implements ClientExchange {

    private final ClientRequest request;
    private final boolean requiresContinue;
    private final AjpClientConnection clientConnection;

    private ClientCallback<ClientExchange> responseCallback;
    private ClientCallback<ClientExchange> readyCallback;
    private ContinueNotification continueNotification;

    private ClientResponse response;
    private ClientResponse continueResponse;
    private IOException failedReason;

    private AjpClientResponseStreamSourceChannel responseChannel;
    private AjpClientRequestClientStreamSinkChannel requestChannel;

    private int state = 0;
    private static final int REQUEST_TERMINATED = 1;
    private static final int RESPONSE_TERMINATED = 1 << 1;

    AjpClientExchange(ClientCallback<ClientExchange> readyCallback, ClientRequest request, AjpClientConnection clientConnection) {
        this.readyCallback = readyCallback;
        this.request = request;
        this.clientConnection = clientConnection;
        boolean reqContinue = false;
        if (request.getRequestHeaders().contains(Headers.EXPECT)) {
            for (String header : request.getRequestHeaders().get(Headers.EXPECT)) {
                if (header.equals("100-continue")) {
                    reqContinue = true;
                }
            }
        }
        this.requiresContinue = reqContinue;
    }

    void terminateRequest() {
        state |= REQUEST_TERMINATED;
        if (anyAreSet(state, RESPONSE_TERMINATED)) {
            clientConnection.requestDone();
        }
    }

    void terminateResponse() {
        state |= RESPONSE_TERMINATED;
        if (anyAreSet(state, REQUEST_TERMINATED)) {
            clientConnection.requestDone();
        }
    }

    public boolean isRequiresContinue() {
        return requiresContinue;
    }


    void setContinueResponse(ClientResponse response) {
        this.continueResponse = response;
        if (continueNotification != null) {
            this.continueNotification.handleContinue(this);
        }
    }

    void setResponse(ClientResponse response) {
        this.response = response;
        if (responseCallback != null) {
            this.responseCallback.completed(this);
        }
    }

    @Override
    public void setResponseListener(ClientCallback<ClientExchange> listener) {
        this.responseCallback = listener;
        if (listener != null) {
            if (failedReason != null) {
                listener.failed(failedReason);
            } else if (response != null) {
                listener.completed(this);
            }
        }
    }

    @Override
    public void setContinueHandler(ContinueNotification continueHandler) {
        this.continueNotification = continueHandler;
    }

    @Override
    public void setPushHandler(PushCallback pushCallback) {

    }

    void setFailed(IOException e) {
        this.failedReason = e;
        if (readyCallback != null) {
            readyCallback.failed(e);
            readyCallback = null;
        }
        if (responseCallback != null) {
            responseCallback.failed(e);
            responseCallback = null;
        }
    }

    @Override
    public StreamSinkChannel getRequestChannel() {
        return new DetachableStreamSinkChannel(requestChannel) {
            @Override
            protected boolean isFinished() {
                return anyAreSet(state, REQUEST_TERMINATED);
            }
        };
    }

    @Override
    public StreamSourceChannel getResponseChannel() {
        return new DetachableStreamSourceChannel(responseChannel) {
            @Override
            protected boolean isFinished() {
                return anyAreSet(state, RESPONSE_TERMINATED);
            }
        };
    }

    @Override
    public ClientRequest getRequest() {
        return request;
    }

    @Override
    public ClientResponse getResponse() {
        return response;
    }

    @Override
    public ClientResponse getContinueResponse() {
        return continueResponse;
    }

    @Override
    public ClientConnection getConnection() {
        return clientConnection;
    }

    void setResponseChannel(AjpClientResponseStreamSourceChannel responseChannel) {
        this.responseChannel = responseChannel;
    }

    void setRequestChannel(AjpClientRequestClientStreamSinkChannel requestChannel) {
        this.requestChannel = requestChannel;
    }

    void invokeReadReadyCallback(final ClientExchange result) {
        if(readyCallback != null) {
            readyCallback.completed(result);
            readyCallback = null;
        }
    }
}
