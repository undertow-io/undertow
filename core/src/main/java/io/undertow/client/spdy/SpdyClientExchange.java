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

package io.undertow.client.spdy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ContinueNotification;
import io.undertow.client.PushCallback;
import io.undertow.protocols.spdy.SpdyStreamSinkChannel;
import io.undertow.protocols.spdy.SpdyStreamSourceChannel;
import io.undertow.protocols.spdy.SpdySynReplyStreamSourceChannel;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class SpdyClientExchange extends AbstractAttachable implements ClientExchange {
    private ClientCallback<ClientExchange> responseListener;
    private ContinueNotification continueNotification;
    private SpdyStreamSourceChannel response;
    private ClientResponse clientResponse;
    private final ClientConnection clientConnection;
    private final SpdyStreamSinkChannel request;
    private final ClientRequest clientRequest;
    private IOException failedReason;
    private PushCallback pushCallback;

    public SpdyClientExchange(ClientConnection clientConnection, SpdyStreamSinkChannel request, ClientRequest clientRequest) {
        this.clientConnection = clientConnection;
        this.request = request;
        this.clientRequest = clientRequest;
    }

    @Override
    public void setResponseListener(ClientCallback<ClientExchange> responseListener) {
        this.responseListener = responseListener;
        if (responseListener != null) {
            if (failedReason != null) {
                responseListener.failed(failedReason);
            } else if (clientResponse != null) {
                responseListener.completed(this);
            }
        }
    }

    @Override
    public void setContinueHandler(ContinueNotification continueHandler) {
        String expect = clientRequest.getRequestHeaders().getFirst(Headers.EXPECT);
        if ("100-continue".equalsIgnoreCase(expect)) {
            continueHandler.handleContinue(this);
        }
    }

    @Override
    public void setPushHandler(PushCallback pushCallback) {
        this.pushCallback = pushCallback;
    }

    PushCallback getPushCallback() {
        return pushCallback;
    }

    @Override
    public StreamSinkChannel getRequestChannel() {
        return request;
    }

    @Override
    public StreamSourceChannel getResponseChannel() {
        return response;
    }

    @Override
    public ClientRequest getRequest() {
        return clientRequest;
    }

    @Override
    public ClientResponse getResponse() {
        return clientResponse;
    }

    @Override
    public ClientResponse getContinueResponse() {
        return null;
    }

    @Override
    public ClientConnection getConnection() {
        return clientConnection;
    }

    void failed(final IOException e) {
        this.failedReason = e;
        if(responseListener != null) {
            responseListener.failed(e);
        }
    }

    void responseReady(SpdySynReplyStreamSourceChannel result) {
        this.response = result;
        HeaderMap headers = result.getHeaders();
        final String status = result.getHeaders().getFirst(SpdyClientConnection.STATUS);
        int statusCode = 500;
        if (status != null && status.length() > 3) {
            statusCode = Integer.parseInt(status.substring(0, 3));
        }
        headers.remove(SpdyClientConnection.VERSION);
        headers.remove(SpdyClientConnection.STATUS);
        clientResponse = new ClientResponse(statusCode, status != null ? status.substring(3) : "", clientRequest.getProtocol(), headers);
        if (responseListener != null) {
            responseListener.completed(this);
        }
    }
}
