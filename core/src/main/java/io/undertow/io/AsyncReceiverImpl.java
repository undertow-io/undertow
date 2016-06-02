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

package io.undertow.io;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
public class AsyncReceiverImpl implements Receiver {


    private static final ErrorCallback END_EXCHANGE = new ErrorCallback() {
        @Override
        public void error(HttpServerExchange exchange, IOException e) {
            e.printStackTrace();
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            exchange.endExchange();
        }
    };
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final HttpServerExchange exchange;
    private final StreamSourceChannel channel;

    private int maxBufferSize = -1;
    private boolean paused = false;
    private boolean done = false;

    public AsyncReceiverImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.channel = exchange.getRequestChannel();
        if (channel == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
    }

    @Override
    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public void receiveFullString(final FullStringCallback callback, ErrorCallback errorCallback) {
        receiveFullString(callback, errorCallback, StandardCharsets.ISO_8859_1);
    }

    @Override
    public void receiveFullString(FullStringCallback callback) {
        receiveFullString(callback, END_EXCHANGE, StandardCharsets.ISO_8859_1);
    }

    @Override
    public void receivePartialString(PartialStringCallback callback, ErrorCallback errorCallback) {
        receivePartialString(callback, errorCallback, StandardCharsets.ISO_8859_1);
    }

    @Override
    public void receivePartialString(PartialStringCallback callback) {
        receivePartialString(callback, END_EXCHANGE, StandardCharsets.ISO_8859_1);
    }

    @Override
    public void receiveFullString(final FullStringCallback callback, final ErrorCallback errorCallback, final Charset charset) {
        if(done) {
            throw UndertowMessages.MESSAGES.requestBodyAlreadyRead();
        }
        final ErrorCallback error = errorCallback == null ? END_EXCHANGE : errorCallback;
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (exchange.isRequestComplete()) {
            callback.handle(exchange, "");
            return;
        }
        String contentLengthString = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        long contentLength;
        final ByteArrayOutputStream sb;
        if (contentLengthString != null) {
            contentLength = Long.parseLong(contentLengthString);
            if (contentLength > Integer.MAX_VALUE) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
            sb = new ByteArrayOutputStream((int) contentLength);
        } else {
            contentLength = -1;
            sb = new ByteArrayOutputStream();
        }
        if (maxBufferSize > 0) {
            if (contentLength > maxBufferSize) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        }
        PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        try {
            int res;
            do {
                try {
                    buffer.clear();
                    res = channel.read(buffer);
                    if (res == -1) {
                        done = true;
                        callback.handle(exchange, sb.toString(charset.name()));
                        return;
                    } else if (res == 0) {
                        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                            @Override
                            public void handleEvent(StreamSourceChannel channel) {
                                if(done) {
                                    return;
                                }
                                PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                                final ByteBuffer buffer = pooled.getBuffer();
                                try {
                                    int res;
                                    do {
                                        try {
                                            buffer.clear();
                                            res = channel.read(buffer);
                                            if (res == -1) {
                                                done = true;
                                                Connectors.executeRootHandler(new HttpHandler() {
                                                    @Override
                                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                        callback.handle(exchange, sb.toString(charset.name()));
                                                    }
                                                }, exchange);
                                                return;
                                            } else if (res == 0) {
                                                return;
                                            } else {
                                                buffer.flip();
                                                while (buffer.hasRemaining()) {
                                                    sb.write(buffer.get());
                                                }
                                                if (maxBufferSize > 0 && sb.size() > maxBufferSize) {
                                                    Connectors.executeRootHandler(new HttpHandler() {
                                                        @Override
                                                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                            error.error(exchange, new RequestToLargeException());
                                                        }
                                                    }, exchange);
                                                    return;
                                                }
                                            }
                                        } catch (final IOException e) {

                                            Connectors.executeRootHandler(new HttpHandler() {
                                                @Override
                                                public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                    error.error(exchange, e);
                                                }
                                            }, exchange);
                                            return;
                                        }
                                    } while (true);
                                } finally {
                                    pooled.close();
                                }
                            }
                        });
                        channel.resumeReads();
                        return;
                    } else {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            sb.write(buffer.get());
                        }
                        if (maxBufferSize > 0 && sb.size() > maxBufferSize) {
                            error.error(exchange, new RequestToLargeException());
                            return;
                        }
                    }
                } catch (IOException e) {
                    error.error(exchange, e);
                    return;
                }
            } while (true);
        } finally {
            pooled.close();
        }

    }

    @Override
    public void receiveFullString(FullStringCallback callback, Charset charset) {
        receiveFullString(callback, END_EXCHANGE, charset);
    }

    @Override
    public void receivePartialString(final PartialStringCallback callback, final ErrorCallback errorCallback, Charset charset) {
        if(done) {
            throw UndertowMessages.MESSAGES.requestBodyAlreadyRead();
        }
        final ErrorCallback error = errorCallback == null ? END_EXCHANGE : errorCallback;
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (exchange.isRequestComplete()) {
            callback.handle(exchange, "", true);
            return;
        }
        String contentLengthString = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        long contentLength;
        if (contentLengthString != null) {
            contentLength = Long.parseLong(contentLengthString);
            if (contentLength > Integer.MAX_VALUE) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        } else {
            contentLength = -1;
        }
        if (maxBufferSize > 0) {
            if (contentLength > maxBufferSize) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        }
        final CharsetDecoder decoder = charset.newDecoder();
        PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            @Override
            public void handleEvent(final StreamSourceChannel channel) {
                if(done || paused) {
                    return;
                }
                PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                final ByteBuffer buffer = pooled.getBuffer();
                try {
                    int res;
                    do {
                        if(paused) {
                            return;
                        }
                        try {
                            buffer.clear();
                            res = channel.read(buffer);
                            if (res == -1) {
                                done = true;
                                Connectors.executeRootHandler(new HttpHandler() {
                                    @Override
                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                        callback.handle(exchange, "", true);
                                    }
                                }, exchange);
                                return;
                            } else if (res == 0) {
                                return;
                            } else {
                                buffer.flip();
                                final CharBuffer cb = decoder.decode(buffer);
                                Connectors.executeRootHandler(new HttpHandler() {
                                    @Override
                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                        callback.handle(exchange, cb.toString(), false);
                                        if (!paused) {
                                            channel.resumeReads();
                                        } else {
                                            System.out.println("paused");
                                        }
                                    }
                                }, exchange);
                            }
                        } catch (final IOException e) {
                            Connectors.executeRootHandler(new HttpHandler() {
                                @Override
                                public void handleRequest(HttpServerExchange exchange) throws Exception {
                                    error.error(exchange, e);
                                }
                            }, exchange);
                            return;
                        }
                    } while (true);
                } finally {
                    pooled.close();
                }
            }
        });
        try {
            int res;
            do {
                try {
                    buffer.clear();
                    res = channel.read(buffer);
                    if (res == -1) {
                        done = true;
                        callback.handle(exchange, "", true);
                        return;
                    } else if (res == 0) {
                        channel.resumeReads();
                        return;
                    } else {
                        buffer.flip();
                        CharBuffer cb = decoder.decode(buffer);
                        callback.handle(exchange, cb.toString(), false);
                        if(paused) {
                            return;
                        }
                    }
                } catch (IOException e) {
                    error.error(exchange, e);
                    return;
                }
            } while (true);
        } finally {
            pooled.close();
        }

    }

    @Override
    public void receivePartialString(PartialStringCallback callback, Charset charset) {
        receivePartialString(callback, END_EXCHANGE, charset);
    }

    @Override
    public void receiveFullBytes(final FullBytesCallback callback, final ErrorCallback errorCallback) {

        if(done) {
            throw UndertowMessages.MESSAGES.requestBodyAlreadyRead();
        }
        final ErrorCallback error = errorCallback == null ? END_EXCHANGE : errorCallback;
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (exchange.isRequestComplete()) {
            callback.handle(exchange, EMPTY_BYTE_ARRAY);
            return;
        }
        String contentLengthString = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        long contentLength;
        final ByteArrayOutputStream sb;
        if (contentLengthString != null) {
            contentLength = Long.parseLong(contentLengthString);
            if (contentLength > Integer.MAX_VALUE) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
            sb = new ByteArrayOutputStream((int) contentLength);
        } else {
            contentLength = -1;
            sb = new ByteArrayOutputStream();
        }
        if (maxBufferSize > 0) {
            if (contentLength > maxBufferSize) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        }
        PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        try {
            int res;
            do {
                try {
                    buffer.clear();
                    res = channel.read(buffer);
                    if (res == -1) {
                        done = true;
                        callback.handle(exchange, sb.toByteArray());
                        return;
                    } else if (res == 0) {
                        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                            @Override
                            public void handleEvent(StreamSourceChannel channel) {
                                if(done) {
                                    return;
                                }
                                PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                                final ByteBuffer buffer = pooled.getBuffer();
                                try {
                                    int res;
                                    do {
                                        try {
                                            buffer.clear();
                                            res = channel.read(buffer);
                                            if (res == -1) {
                                                done = true;
                                                Connectors.executeRootHandler(new HttpHandler() {
                                                    @Override
                                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                        callback.handle(exchange, sb.toByteArray());
                                                    }
                                                }, exchange);
                                                return;
                                            } else if (res == 0) {
                                                return;
                                            } else {
                                                buffer.flip();
                                                while (buffer.hasRemaining()) {
                                                    sb.write(buffer.get());
                                                }
                                                if (maxBufferSize > 0 && sb.size() > maxBufferSize) {
                                                    Connectors.executeRootHandler(new HttpHandler() {
                                                        @Override
                                                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                            error.error(exchange, new RequestToLargeException());
                                                        }
                                                    }, exchange);
                                                    return;
                                                }
                                            }
                                        } catch (final Exception e) {
                                            Connectors.executeRootHandler(new HttpHandler() {
                                                @Override
                                                public void handleRequest(HttpServerExchange exchange) throws Exception {
                                                    error.error(exchange, new IOException(e));
                                                }
                                            }, exchange);
                                            return;
                                        }
                                    } while (true);
                                } finally {
                                    pooled.close();
                                }
                            }
                        });
                        channel.resumeReads();
                        return;
                    } else {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            sb.write(buffer.get());
                        }
                        if (maxBufferSize > 0 && sb.size() > maxBufferSize) {
                            error.error(exchange, new RequestToLargeException());
                            return;
                        }
                    }
                } catch (IOException e) {
                    error.error(exchange, e);
                    return;
                }
            } while (true);
        } finally {
            pooled.close();
        }
    }

    @Override
    public void receiveFullBytes(FullBytesCallback callback) {
        receiveFullBytes(callback, END_EXCHANGE);
    }

    @Override
    public void receivePartialBytes(final PartialBytesCallback callback, final ErrorCallback errorCallback) {
        if(done) {
            throw UndertowMessages.MESSAGES.requestBodyAlreadyRead();
        }
        final ErrorCallback error = errorCallback == null ? END_EXCHANGE : errorCallback;
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (exchange.isRequestComplete()) {
            callback.handle(exchange, EMPTY_BYTE_ARRAY, true);
            return;
        }
        String contentLengthString = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        long contentLength;
        if (contentLengthString != null) {
            contentLength = Long.parseLong(contentLengthString);
            if (contentLength > Integer.MAX_VALUE) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        } else {
            contentLength = -1;
        }
        if (maxBufferSize > 0) {
            if (contentLength > maxBufferSize) {
                error.error(exchange, new RequestToLargeException());
                return;
            }
        }
        PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
            @Override
            public void handleEvent(final StreamSourceChannel channel) {
                if(done || paused) {
                    return;
                }
                PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                final ByteBuffer buffer = pooled.getBuffer();
                try {
                    int res;
                    do {
                        if(paused) {
                            return;
                        }
                        try {
                            buffer.clear();
                            res = channel.read(buffer);
                            if (res == -1) {
                                done = true;
                                Connectors.executeRootHandler(new HttpHandler() {
                                    @Override
                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                        callback.handle(exchange, EMPTY_BYTE_ARRAY, true);
                                    }
                                }, exchange);
                                return;
                            } else if (res == 0) {
                                return;
                            } else {
                                buffer.flip();
                                final byte[] data = new byte[buffer.remaining()];
                                buffer.get(data);

                                Connectors.executeRootHandler(new HttpHandler() {
                                    @Override
                                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                                        callback.handle(exchange, data, false);
                                        if (!paused) {
                                            channel.resumeReads();
                                        }
                                    }
                                }, exchange);
                            }
                        } catch (final IOException e) {
                            Connectors.executeRootHandler(new HttpHandler() {
                                @Override
                                public void handleRequest(HttpServerExchange exchange) throws Exception {
                                    error.error(exchange, e);
                                }
                            }, exchange);
                            return;
                        }
                    } while (true);
                } finally {
                    pooled.close();
                }
            }
        });
        try {
            int res;
            do {
                try {
                    buffer.clear();
                    res = channel.read(buffer);
                    if (res == -1) {
                        done = true;
                        callback.handle(exchange, EMPTY_BYTE_ARRAY, true);
                        return;
                    } else if (res == 0) {

                        channel.resumeReads();
                        return;
                    } else {
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        callback.handle(exchange, data, false);
                        if(paused) {
                            return;
                        }
                    }
                } catch (IOException e) {
                    error.error(exchange, e);
                    return;
                }
            } while (true);
        } finally {
            pooled.close();
        }
    }

    @Override
    public void receivePartialBytes(PartialBytesCallback callback) {
        receivePartialBytes(callback, END_EXCHANGE);
    }

    @Override
    public void pause() {
        this.paused = true;
        channel.suspendReads();
    }

    @Override
    public void resume() {
        this.paused = false;
        channel.wakeupReads();
    }
}
