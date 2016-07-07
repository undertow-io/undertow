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

package io.undertow.server.protocol.http;

import io.undertow.UndertowMessages;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.AbstractServerConnection;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ConnectionSSLSessionInfo;
import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.server.Connectors;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.ImmediatePooledByteBuffer;
import io.undertow.util.Methods;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.StreamSinkConduit;

import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;

/**
 * A server-side HTTP connection.
 * <p>
 * Note that the lifecycle of the server connection is tied to the underlying TCP connection. Even if the channel
 * is upgraded the connection is not considered closed until the upgraded channel is closed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerConnection extends AbstractServerConnection {

    private SSLSessionInfo sslSessionInfo;
    private HttpReadListener readListener;
    private PipeliningBufferingStreamSinkConduit pipelineBuffer;
    private HttpResponseConduit responseConduit;
    private ServerFixedLengthStreamSinkConduit fixedLengthStreamSinkConduit;
    private ReadDataStreamSourceConduit readDataStreamSourceConduit;

    private HttpUpgradeListener upgradeListener;
    private boolean connectHandled;

    public HttpServerConnection(StreamConnection channel, final ByteBufferPool bufferPool, final HttpHandler rootHandler, final OptionMap undertowOptions, final int bufferSize, final ConnectorStatisticsImpl connectorStatistics) {
        super(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
        if (channel instanceof SslChannel) {
            sslSessionInfo = new ConnectionSSLSessionInfo(((SslChannel) channel), this);
        }
        this.responseConduit = new HttpResponseConduit(channel.getSinkChannel().getConduit(), bufferPool);

        fixedLengthStreamSinkConduit = new ServerFixedLengthStreamSinkConduit(responseConduit, false, false);
        readDataStreamSourceConduit = new ReadDataStreamSourceConduit(channel.getSourceChannel().getConduit(), this);
        //todo: do this without an allocation
        addCloseListener(new CloseListener() {
            @Override
            public void closed(ServerConnection connection) {
                if(connectorStatistics != null) {
                    connectorStatistics.decrementConnectionCount();
                }
                responseConduit.freeBuffers();
            }
        });
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
        exchange.setRequestURI(exchange.getRequestURI(), exchange.isHostIncludedInRequestURI());
        exchange.setRequestPath(exchange.getRequestPath());
        exchange.setRelativePath(exchange.getRelativePath());
        newExchange.getRequestHeaders().put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
        newExchange.getRequestHeaders().put(Headers.CONTENT_LENGTH, 0);
        newExchange.setPersistent(true);

        Connectors.terminateRequest(newExchange);
        newExchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {

                ServerFixedLengthStreamSinkConduit fixed = new ServerFixedLengthStreamSinkConduit(new HttpResponseConduit(getSinkChannel().getConduit(), getByteBufferPool(), exchange), false, false);
                fixed.reset(0, exchange);
                return fixed;
            }
        });

        //we restore the read channel immediately, as this out of band response has no read side
        channel.getSourceChannel().setConduit(source(state));
        newExchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                restoreChannel(state);
            }
        });
        return newExchange;
    }

    @Override
    public boolean isContinueResponseSupported() {
        return true;
    }

    @Override
    public void terminateRequestChannel(HttpServerExchange exchange) {

    }

    /**
     * Pushes back the given data. This should only be used by transfer coding handlers that have read past
     * the end of the request when handling pipelined requests
     *
     * @param unget The buffer to push back
     */
    public void ungetRequestBytes(final PooledByteBuffer unget) {
        if (getExtraBytes() == null) {
            setExtraBytes(unget);
        } else {
            PooledByteBuffer eb = getExtraBytes();
            ByteBuffer buf = eb.getBuffer();
            final ByteBuffer ugBuffer = unget.getBuffer();

            if (ugBuffer.limit() - ugBuffer.remaining() > buf.remaining()) {
                //stuff the existing data after the data we are ungetting
                ugBuffer.compact();
                ugBuffer.put(buf);
                ugBuffer.flip();
                eb.close();
                setExtraBytes(unget);
            } else {
                //TODO: this is horrible, but should not happen often
                final byte[] data = new byte[ugBuffer.remaining() + buf.remaining()];
                int first = ugBuffer.remaining();
                ugBuffer.get(data, 0, ugBuffer.remaining());
                buf.get(data, first, buf.remaining());
                eb.close();
                unget.close();
                final ByteBuffer newBuffer = ByteBuffer.wrap(data);
                setExtraBytes(new ImmediatePooledByteBuffer(newBuffer));
            }
        }
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return sslSessionInfo;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
        this.sslSessionInfo = sessionInfo;
    }

    public SSLSession getSslSession() {
        if (channel instanceof SslChannel) {
            return ((SslChannel) channel).getSslSession();
        }
        return null;
    }

    @Override
    protected StreamConnection upgradeChannel() {
        clearChannel();
        if (extraBytes != null) {
            channel.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(channel.getSourceChannel().getConduit(), this));
        }
        return channel;
    }

    @Override
    protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
        if(exchange.getRequestMethod().equals(Methods.CONNECT) && !connectHandled) {
            //make sure that any unhandled CONNECT requests result in a connection close
            exchange.setPersistent(false);
            exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
        }
        return HttpTransferEncoding.createSinkConduit(exchange);
    }

    @Override
    protected boolean isUpgradeSupported() {
        return true;
    }

    @Override
    protected boolean isConnectSupported() {
        return true;
    }

    void setReadListener(HttpReadListener readListener) {
        this.readListener = readListener;
    }

    @Override
    protected void exchangeComplete(HttpServerExchange exchange) {
        if(fixedLengthStreamSinkConduit != null) {
            fixedLengthStreamSinkConduit.clearExchange();
        }
        if (pipelineBuffer == null) {
            readListener.exchangeComplete(exchange);
        } else {
            pipelineBuffer.exchangeComplete(exchange);
        }
    }

    HttpReadListener getReadListener() {
        return readListener;
    }

    ReadDataStreamSourceConduit getReadDataStreamSourceConduit() {
        return readDataStreamSourceConduit;
    }

    public PipeliningBufferingStreamSinkConduit getPipelineBuffer() {
        return pipelineBuffer;
    }

    public HttpResponseConduit getResponseConduit() {
        return responseConduit;
    }

    ServerFixedLengthStreamSinkConduit getFixedLengthStreamSinkConduit() {
        return fixedLengthStreamSinkConduit;
    }

    protected HttpUpgradeListener getUpgradeListener() {
        return upgradeListener;
    }

    @Override
    protected void setUpgradeListener(HttpUpgradeListener upgradeListener) {
        this.upgradeListener = upgradeListener;
    }

    @Override
    protected void setConnectListener(HttpUpgradeListener connectListener) {
        this.upgradeListener = connectListener;
        connectHandled = true;
    }

    void setCurrentExchange(HttpServerExchange exchange) {
        this.current = exchange;
    }

    public void setPipelineBuffer(PipeliningBufferingStreamSinkConduit pipelineBuffer) {
        this.pipelineBuffer = pipelineBuffer;
        this.responseConduit = new HttpResponseConduit(pipelineBuffer, bufferPool);
        this.fixedLengthStreamSinkConduit = new ServerFixedLengthStreamSinkConduit(responseConduit, false, false);
    }

    @Override
    public String getTransportProtocol() {
        return "http/1.1";
    }

    boolean isConnectHandled() {
        return connectHandled;
    }
}
