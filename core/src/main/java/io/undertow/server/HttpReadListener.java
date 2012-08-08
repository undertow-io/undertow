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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.server.httpparser.HttpExchangeBuilder;
import io.undertow.server.httpparser.HttpParser;
import io.undertow.server.httpparser.ParseState;
import io.undertow.util.GatedStreamSinkChannel;
import io.undertow.util.HeaderMap;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<PushBackStreamChannel> {

    private final StreamSinkChannel responseChannel;

    private ParseState state;
    private HttpExchangeBuilder builder;

    private final HttpServerConnection connection;

    HttpReadListener(final StreamSinkChannel responseChannel, final HttpServerConnection connection) {
        this.responseChannel = responseChannel;
        this.connection = connection;
    }

    public void handleEvent(final PushBackStreamChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;
        try {
            final int res;
            try {
                res = channel.read(buffer);
            } catch (IOException e) {
                if(UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                }
                safeClose(channel);
                return;
            }
            if (res == 0) {
                return;
            }
            if (res == -1) {
                try {
                    channel.shutdownReads();
                    final StreamSinkChannel responseChannel = this.responseChannel;
                    responseChannel.shutdownWrites();
                    // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                    if (! responseChannel.flush()) {
                        responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                        responseChannel.resumeWrites();
                    }
                } catch (IOException e) {
                    if(UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                    }
                    // fuck it, it's all ruined
                    IoUtils.safeClose(channel);
                    return;
                }
                return;
            }
            //TODO: we need to handle parse errors
            buffer.flip();
            if(state == null) {
                state = new ParseState();
                builder = new HttpExchangeBuilder();
            }
            int remaining = HttpParser.INSTANCE.handle(buffer, res, state, builder);
            if(remaining > 0) {
                free = false;
                channel.unget(pooled);
            }

            if(state.isComplete()) {
                // we remove ourselves as the read listener from the channel;
                // if the http handler doesn't set any then reads will suspend, which is the right thing to do
                channel.getReadSetter().set(null);
                final StreamSinkChannel ourResponseChannel = this.responseChannel;
                final StreamSinkChannel targetChannel = ourResponseChannel instanceof GatedStreamSinkChannel ? ((GatedStreamSinkChannel)ourResponseChannel).getChannel() : ourResponseChannel;
                final GatedStreamSinkChannel nextRequestResponseChannel = new GatedStreamSinkChannel(targetChannel, this, false, true);
                final HeaderMap requestHeaders = builder.getHeaders();
                final HeaderMap responseHeaders = new HeaderMap();
                final Map<String,List<String>> parameters = builder.getQueryParameters();
                final String method = builder.getMethod();
                final String protocol = builder.getProtocol();

                final Runnable requestTerminateAction = new Runnable() {
                    public void run() {
                        channel.getReadSetter().set(new HttpReadListener(nextRequestResponseChannel, connection));
                        channel.resumeReads();
                    }
                };
                final Runnable responseTerminateAction = new Runnable() {
                    public void run() {
                        nextRequestResponseChannel.openGate(HttpReadListener.this);
                    }
                };
                final HttpServerExchange httpServerExchange = new HttpServerExchange(connection, requestHeaders, responseHeaders, parameters, method, protocol, channel, ourResponseChannel, requestTerminateAction, responseTerminateAction);

                try {
                    httpServerExchange.setRelativePath(builder.getRelativePath());
                    httpServerExchange.setRequestPath(builder.getPath());

                    state = null;
                    builder = null;
                    connection.getRootHandler().handleRequest(httpServerExchange, new HttpCompletionHandler() {
                        public void handleComplete() {
                            httpServerExchange.cleanup();
                        }
                    });

                } catch (Throwable t) {
                    //TODO: we should attempt to return a 500 status code in this situation
                    UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                    IoUtils.safeClose(nextRequestResponseChannel);
                    IoUtils.safeClose(channel);
                }
            }
        } finally {
            if (free) pooled.free();
        }
    }

}
