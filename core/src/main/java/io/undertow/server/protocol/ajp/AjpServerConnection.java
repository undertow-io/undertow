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

package io.undertow.server.protocol.ajp;

import io.undertow.UndertowMessages;
import io.undertow.server.AbstractServerConnection;
import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.DateUtils;

import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

/**
 * A server-side AJP connection.
 * <p>
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AjpServerConnection extends AbstractServerConnection {
    private SSLSessionInfo sslSessionInfo;
    private WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel> writeReadyHandler;
    private AjpReadListener ajpReadListener;

    public AjpServerConnection(StreamConnection channel, ByteBufferPool bufferPool, HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize) {
        super(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
        this.writeReadyHandler = new WriteReadyHandler.ChannelListenerHandler<>(channel.getSinkChannel());
    }

    @Override
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        throw UndertowMessages.MESSAGES.outOfBandResponseNotSupported();
    }

    @Override
    public boolean isContinueResponseSupported() {
        return false;
    }

    @Override
    public void terminateRequestChannel(HttpServerExchange exchange) {
        if (!exchange.isPersistent()) {
            IoUtils.safeClose(getChannel().getSourceChannel());
        }
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
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
        DateUtils.addDateHeaderIfRequired(exchange);
        return conduit;
    }

    @Override
    protected boolean isUpgradeSupported() {
        return false;
    }

    @Override
    protected boolean isConnectSupported() {
        return false;
    }

    void setAjpReadListener(AjpReadListener ajpReadListener) {
        this.ajpReadListener = ajpReadListener;
    }

    @Override
    protected void exchangeComplete(HttpServerExchange exchange) {
        ajpReadListener.exchangeComplete(exchange);
    }

    @Override
    protected void setConnectListener(HttpUpgradeListener connectListener) {
        throw UndertowMessages.MESSAGES.connectNotSupported();
    }

    void setCurrentExchange(HttpServerExchange exchange) {
        this.current = exchange;
    }

    @Override
    public String getTransportProtocol() {
        return "ajp";
    }

    @Override
    public boolean isRequestTrailerFieldsSupported() {
        return false;
    }
}
