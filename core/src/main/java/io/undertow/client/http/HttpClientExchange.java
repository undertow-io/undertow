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

package io.undertow.client.http;

import io.undertow.channels.DetachableStreamSinkChannel;
import io.undertow.channels.DetachableStreamSourceChannel;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ContinueNotification;
import io.undertow.client.PushCallback;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Headers;
import org.jboss.logging.Logger;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;

import static org.xnio.Bits.anyAreSet;

/**
 * @author Stuart Douglas
 */
class HttpClientExchange extends AbstractAttachable implements ClientExchange {

    private static final Logger log = Logger.getLogger(HttpClientExchange.class.getName());

    private final ClientRequest request;
    private final boolean requiresContinue;
    private final HttpClientConnection clientConnection;

    private ClientCallback<ClientExchange> responseCallback;
    private ClientCallback<ClientExchange> readyCallback;
    private ContinueNotification continueNotification;

    private ClientResponse response;
    private ClientResponse continueResponse;
    private IOException failedReason;
    private HttpRequestConduit requestConduit;

    private int state = 0;
    private static final int REQUEST_TERMINATED = 1;
    private static final int RESPONSE_TERMINATED = 1 << 1;

    HttpClientExchange(ClientCallback<ClientExchange> readyCallback, ClientRequest request, HttpClientConnection clientConnection) {
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

    public void setRequestConduit(HttpRequestConduit requestConduit) {
        this.requestConduit = requestConduit;
    }

    void terminateRequest() {
        if(anyAreSet(state, REQUEST_TERMINATED)) {
            return;
        }
        log.debugf("request terminated for request to %s %s", clientConnection.getPeerAddress(), getRequest().getPath());
        state |= REQUEST_TERMINATED;
        clientConnection.requestDataSent();
        if (anyAreSet(state, RESPONSE_TERMINATED)) {
            clientConnection.exchangeDone();
        }
    }

    boolean isRequestDataSent() {
        return anyAreSet(state, REQUEST_TERMINATED);
    }

    void terminateResponse() {
        if(anyAreSet(state, RESPONSE_TERMINATED)) {
            return;
        }
        log.debugf("response terminated for request to %s %s", clientConnection.getPeerAddress(), getRequest().getPath());
        state |= RESPONSE_TERMINATED;
        if (anyAreSet(state, REQUEST_TERMINATED)) {
            clientConnection.exchangeDone();
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
            try {
                Thread.sleep(500);
                } catch(InterruptedException e) {}
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
        if(requestConduit != null) {
            requestConduit.freeBuffers();
        }
    }

    @Override
    public StreamSinkChannel getRequestChannel() {
        return new DetachableStreamSinkChannel(clientConnection.getConnection().getSinkChannel()) {
            @Override
            protected boolean isFinished() {
                return anyAreSet(state, REQUEST_TERMINATED);
            }
        };
    }

    @Override
    public StreamSourceChannel getResponseChannel() {
        return new DetachableStreamSourceChannel(clientConnection.getConnection().getSourceChannel()) {
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

    ClientCallback<ClientExchange> getResponseCallback() {
        return responseCallback;
    }

    void invokeReadReadyCallback() {
        if(readyCallback != null) {
            readyCallback.completed(this);
            readyCallback = null;
        }
    }
}
