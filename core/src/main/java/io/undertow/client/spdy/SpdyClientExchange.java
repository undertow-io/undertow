package io.undertow.client.spdy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ContinueNotification;
import io.undertow.spdy.SpdyStreamSinkChannel;
import io.undertow.spdy.SpdyStreamSourceChannel;
import io.undertow.spdy.SpdySynReplyStreamSourceChannel;
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

    public SpdyClientExchange(ClientConnection clientConnection, SpdyStreamSinkChannel request, ClientRequest clientRequest) {
        this.clientConnection = clientConnection;
        this.request = request;
        this.clientRequest = clientRequest;
    }


    @Override
    public void setResponseListener(ClientCallback<ClientExchange> responseListener) {
        this.responseListener = responseListener;
    }

    @Override
    public void setContinueHandler(ContinueNotification continueHandler) {
        String expect = clientRequest.getRequestHeaders().getFirst(Headers.EXPECT);
        if ("100-continue".equalsIgnoreCase(expect)) {
            continueHandler.handleContinue(this);
        }
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
        clientResponse = new ClientResponse(statusCode, status.substring(3), clientRequest.getProtocol(), headers);
        if (responseListener != null) {
            responseListener.completed(this);
        }
    }
}
