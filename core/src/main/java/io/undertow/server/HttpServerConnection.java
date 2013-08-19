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

package io.undertow.server;

import io.undertow.UndertowMessages;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.SslChannel;

import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;

/**
 * A server-side HTTP connection.
 * <p/>
 * Note that the lifecycle of the server connection is tied to the underlying TCP connection. Even if the channel
 * is upgraded the connection is not considered closed until the upgraded channel is closed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerConnection extends AbstractServerConnection implements ServerConnection {

    public HttpServerConnection(StreamConnection channel, final Pool<ByteBuffer> bufferPool, final HttpHandler rootHandler, final OptionMap undertowOptions, final int bufferSize) {
        super(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
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
        newExchange.setParsedRequestPath(exchange.getRequestPath());
        newExchange.getRequestHeaders().put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
        newExchange.getRequestHeaders().put(Headers.CONTENT_LENGTH, 0);

        //apply transfer encoding rules
        HttpTransferEncoding.setupRequest(newExchange);

        //we restore the read channel immediately, as this out of band response has no read side
        channel.getSourceChannel().setConduit(state.source);
        newExchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                restoreChannel(state);
            }
        });
        return newExchange;
    }

    /**
     * Pushes back the given data. This should only be used by transfer coding handlers that have read past
     * the end of the request when handling pipelined requests
     *
     * @param unget The buffer to push back
     */
    public void ungetRequestBytes(final Pooled<ByteBuffer> unget) {
        if (getExtraBytes() == null) {
            setExtraBytes(unget);
        } else {
            Pooled<ByteBuffer> eb = getExtraBytes();
            ByteBuffer buf = eb.getResource();
            final ByteBuffer ugBuffer = unget.getResource();

            if (ugBuffer.limit() - ugBuffer.remaining() > buf.remaining()) {
                //stuff the existing data after the data we are ungetting
                ugBuffer.compact();
                ugBuffer.put(buf);
                ugBuffer.flip();
                eb.free();
                setExtraBytes(unget);
            } else {
                //TODO: this is horrible, but should not happen often
                final byte[] data = new byte[ugBuffer.remaining() + buf.remaining()];
                int first = ugBuffer.remaining();
                ugBuffer.get(data, 0, ugBuffer.remaining());
                buf.get(data, first, buf.remaining());
                eb.free();
                unget.free();
                final ByteBuffer newBuffer = ByteBuffer.wrap(data);
                setExtraBytes(new Pooled<ByteBuffer>() {
                    @Override
                    public void discard() {

                    }

                    @Override
                    public void free() {

                    }

                    @Override
                    public ByteBuffer getResource() throws IllegalStateException {
                        return newBuffer;
                    }
                });
            }
        }
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        if (channel instanceof SslChannel) {
            return new DefaultSslSessionInfo(((SslChannel) channel).getSslSession());
        }
        return null;
    }

    public SSLSession getSslSession() {
        if (channel instanceof SslChannel) {
            return ((SslChannel) channel).getSslSession();
        }
        return null;
    }

    @Override
    public StreamConnection upgradeChannel() {
        resetChannel();
        if (extraBytes != null) {
            channel.getSourceChannel().setConduit(new ReadDataStreamSourceConduit(channel.getSourceChannel().getConduit(), this));
        }
        return channel;
    }
}
