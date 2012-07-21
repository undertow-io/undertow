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

package tmp.texugo.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import tmp.texugo.TexugoLogger;
import tmp.texugo.server.httpparser.HttpExchangeBuilder;
import tmp.texugo.server.httpparser.HttpParser;
import tmp.texugo.server.httpparser.ParseState;
import tmp.texugo.util.HeaderMap;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<PushBackStreamChannel> {
    private final Pool<ByteBuffer> bufferPool;


    private final ParseState state = new ParseState();
    private final HttpExchangeBuilder builder = new HttpExchangeBuilder();

    private final HttpHandler rootHandler;
    private final ConnectedStreamChannel underlyingChannel;
    private final HttpServerConnection connection;

    HttpReadListener(final Pool<ByteBuffer> bufferPool, final HttpHandler rootHandler, final ConnectedStreamChannel underlyingChannel) {
        this.bufferPool = bufferPool;
        this.rootHandler = rootHandler;
        this.underlyingChannel = underlyingChannel;
        this.connection = new HttpServerConnection(underlyingChannel);
    }

    public void handleEvent(final PushBackStreamChannel channel) {
        final Pooled<ByteBuffer> pooled = bufferPool.allocate();
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;
        try {
            final int res;
            try {
                res = channel.read(buffer);
            } catch (IOException e) {
                if(TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    TexugoLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
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
                    // TODO: enqueue a write handler which shuts down the write side of the connection
                } catch (IOException e) {
                    if(TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        TexugoLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                    }
                    // fuck it, it's all ruined
                    IoUtils.safeClose(channel);
                    return;
                }
                return;
            }
            //TODO: we need to handle parse errors
            buffer.flip();
            int remaining = HttpParser.INSTANCE.handle(buffer, res, state, builder);
            if(remaining > 0) {
                free = false;
                channel.unget(pooled);
            }

            if(state.isComplete()) {
                final HttpServerExchange httpServerExchange = new HttpServerExchange(bufferPool, connection, builder.getHeaders(), new HeaderMap(), builder.getMethod());
                try {
                    httpServerExchange.setCanonicalPath(builder.getCanonicalPath());
                    httpServerExchange.setRelativePath(builder.getCanonicalPath());
                    httpServerExchange.setRequestPath(builder.getPath());
                    httpServerExchange.setProtocol(builder.getProtocol());
                    httpServerExchange.setRequestChannel(channel);
                    httpServerExchange.setResponseChannel(underlyingChannel);

                    rootHandler.handleRequest(httpServerExchange);
                } catch (Throwable t) {
                    //TODO: we should attempt to return a 500 status code in this situation
                    IoUtils.safeClose(channel);
                    IoUtils.safeClose(underlyingChannel);
                }
            }

            // TODO: Parse the buffer via PFM, set free to false if the buffer is pushed back
        } finally {
            if (free) pooled.free();
        }
    }
}
