/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.protocol.ajp;

import io.undertow.UndertowMessages;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.AbstractServerConnection;
import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.nio.ByteBuffer;

/**
 * A server-side AJP connection.
 * <p/>
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AjpServerConnection extends AbstractServerConnection {
    private SSLSessionInfo sslSessionInfo;
    private WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel> writeReadyHandler;
    private AjpReadListener ajpReadListener;

    public AjpServerConnection(StreamConnection channel, Pool<ByteBuffer> bufferPool, HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize) {
        super(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
        this.writeReadyHandler = new WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel>(channel.getSinkChannel());
    }

    @Override
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        if (exchange == null || !HttpContinue.requiresContinueResponse(exchange)) {
            throw UndertowMessages.MESSAGES.outOfBandResponseOnlyAllowedFor100Continue();
        }
        final ConduitState state = resetChannel();
        HttpServerExchange newExchange = new HttpServerExchange(this);
        for (HttpString header : exchange.getRequestHeaders().getHeaderNames()) {
            newExchange.getRequestHeaders().putAll(header, exchange.getRequestHeaders().get(header));
        }
        newExchange.setProtocol(exchange.getProtocol());
        newExchange.setRequestMethod(exchange.getRequestMethod());
        newExchange.setRequestPath(exchange.getRequestPath());
        newExchange.getRequestHeaders().put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
        newExchange.getRequestHeaders().put(Headers.CONTENT_LENGTH, 0);

        newExchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                restoreChannel(state);
            }
        });
        return newExchange;
    }

    @Override
    public void restoreChannel(ConduitState state) {
        super.restoreChannel(state);
        channel.getSinkChannel().getConduit().setWriteReadyHandler(writeReadyHandler);
    }

    @Override
    public ConduitState resetChannel() {
        ConduitState state = super.resetChannel();
        channel.getSinkChannel().getConduit().setWriteReadyHandler(writeReadyHandler);
        return state;
    }

    @Override
    public void clearChannel() {
        super.clearChannel();
        channel.getSinkChannel().getConduit().setWriteReadyHandler(writeReadyHandler);
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return sslSessionInfo;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
        this.sslSessionInfo = sessionInfo;
    }

    void setSSLSessionInfo(BasicSSLSessionInfo sslSessionInfo) {
        this.sslSessionInfo = sslSessionInfo;
    }

    @Override
    protected StreamConnection upgradeChannel() {
        resetChannel();
        if (extraBytes != null) {
            channel.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(channel.getSourceChannel().getConduit(), this));
        }
        return channel;
    }

    @Override
    protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
        DateUtils.addDateHeaderIfRequired(exchange);
        return conduit;
    }

    @Override
    protected boolean isUpgradeSupported() {
        return true; //TODO: should we support this?
    }

    void setAjpReadListener(AjpReadListener ajpReadListener) {
        this.ajpReadListener = ajpReadListener;
    }

    @Override
    protected void exchangeComplete(HttpServerExchange exchange) {
        ajpReadListener.exchangeComplete(exchange);
    }

    void setCurrentExchange(HttpServerExchange exchange) {
        this.current = exchange;
    }
}
