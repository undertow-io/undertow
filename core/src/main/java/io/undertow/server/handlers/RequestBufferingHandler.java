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

package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.Connectors;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.protocol.http.HttpContinue;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
/**
 *
 * Handler that will buffer all request data
 *
 * @author Stuart Douglas
 */
public class RequestBufferingHandler implements HttpHandler {

    private final HttpHandler next;
    private final int maxBuffers;

    public RequestBufferingHandler(HttpHandler next, int maxBuffers) {
        this.next = next;
        this.maxBuffers = maxBuffers;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {

        if(!exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
            final StreamSourceChannel channel = exchange.getRequestChannel();
            int readBuffers = 0;
            final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
            PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
            try {
                do {
                    int r;
                    ByteBuffer b = buffer.getBuffer();
                    r = channel.read(b);
                    if (r == -1) {
                        if (b.position() == 0) {
                            buffer.close();
                        } else {
                            b.flip();
                            bufferedData[readBuffers] = buffer;
                        }
                        break;
                    } else if (r == 0) {
                        final PooledByteBuffer finalBuffer = buffer;
                        final int finalReadBuffers = readBuffers;
                        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {

                            PooledByteBuffer buffer = finalBuffer;
                            int readBuffers = finalReadBuffers;

                            @Override
                            public void handleEvent(StreamSourceChannel channel) {
                                try {
                                    do {
                                        int r;
                                        ByteBuffer b = buffer.getBuffer();
                                        r = channel.read(b);
                                        if (r == -1) {
                                            if (b.position() == 0) {
                                                buffer.close();
                                            } else {
                                                b.flip();
                                                bufferedData[readBuffers] = buffer;
                                            }
                                            Connectors.ungetRequestBytes(exchange, bufferedData);
                                            Connectors.resetRequestChannel(exchange);
                                            channel.getReadSetter().set(null);
                                            channel.suspendReads();
                                            Connectors.executeRootHandler(next, exchange);
                                            return;
                                        } else if (r == 0) {
                                            return;
                                        } else if (!b.hasRemaining()) {
                                            b.flip();
                                            bufferedData[readBuffers++] = buffer;
                                            if (readBuffers == maxBuffers) {
                                                Connectors.ungetRequestBytes(exchange, bufferedData);
                                                Connectors.resetRequestChannel(exchange);
                                                channel.getReadSetter().set(null);
                                                channel.suspendReads();
                                                Connectors.executeRootHandler(next, exchange);
                                                return;
                                            }
                                            buffer = exchange.getConnection().getByteBufferPool().allocate();
                                        }
                                    } while (true);
                                } catch (Throwable t) {
                                    if (t instanceof IOException) {
                                        UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) t);
                                    } else {
                                        UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                                    }
                                    for (int i = 0; i < bufferedData.length; ++i) {
                                        IoUtils.safeClose(bufferedData[i]);
                                    }
                                    if (buffer != null && buffer.isOpen()) {
                                        IoUtils.safeClose(buffer);
                                    }
                                    exchange.endExchange();
                                }
                            }
                        });
                        channel.resumeReads();
                        return;
                    } else if (!b.hasRemaining()) {
                        b.flip();
                        bufferedData[readBuffers++] = buffer;
                        if (readBuffers == maxBuffers) {
                            break;
                        }
                        buffer = exchange.getConnection().getByteBufferPool().allocate();
                    }
                } while (true);
                Connectors.ungetRequestBytes(exchange, bufferedData);
                Connectors.resetRequestChannel(exchange);
            } catch (Exception | Error e) {
                for (int i = 0; i < bufferedData.length; ++i) {
                    IoUtils.safeClose(bufferedData[i]);
                }
                if (buffer != null && buffer.isOpen()) {
                    IoUtils.safeClose(buffer);
                }
                throw e;
            }
        }
        next.handleRequest(exchange);
    }

    public static final class Wrapper implements HandlerWrapper {

        private final int maxBuffers;

        public Wrapper(int maxBuffers) {
            this.maxBuffers = maxBuffers;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new RequestBufferingHandler(handler, maxBuffers);
        }
    }

    @Override
    public String toString() {
        return "buffer-request( " + maxBuffers + " )";
    }

    public static final class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "buffer-request";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("buffers", Integer.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("buffers");
        }

        @Override
        public String defaultParameter() {
            return "buffers";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((Integer) config.get("buffers"));
        }
    }
}
