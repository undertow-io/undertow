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

package io.undertow.server.handlers.form;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * Handler for submitted form data. This handler takes effect for any request that has a mime type
 * of application/x-www-form-urlencoded. The handler attaches a {@link FormDataParser} to the chain
 * that can parse the underlying form data asynchronously.
 * <p/>
 * Note that this handler is not suitable for use with a blocking handler chain. Blocking handlers
 * should install their own FormDataParser that uses streams.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class FormEncodedDataHandler implements HttpHandler {

    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private String defaultEncoding = "UTF-8";

    public FormEncodedDataHandler(final HttpHandler next) {
        this.next = next;
    }

    public FormEncodedDataHandler() {
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(APPLICATION_X_WWW_FORM_URLENCODED)) {

            String charset = defaultEncoding;
            String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            if (contentType != null) {
                String cs = Headers.extractTokenFromHeader(contentType, "charset");
                if (cs != null) {
                    charset = cs;
                }
            }

            exchange.putAttachment(FormDataParser.ATTACHMENT_KEY, new FormEncodedDataParser(charset, exchange));
        }
        HttpHandlers.executeHandler(next, exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    private static final class FormEncodedDataParser implements ChannelListener<StreamSourceChannel>, FormDataParser {

        private final HttpServerExchange exchange;
        private final FormData data = new FormData();
        private final StringBuilder builder = new StringBuilder();
        private String name = null;
        private volatile ConcreteIoFuture<FormData> ioFuture;
        private String charset;

        //0= parsing name
        //1=parsing name, decode required
        //2=parsing value
        //3=parsing value, decode required
        //4=finished
        private int state = 0;

        private FormEncodedDataParser(final String charset, final HttpServerExchange exchange) {
            this.exchange = exchange;
            this.charset = charset;
        }

        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            int c = 0;
            final Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
            try {
                final ByteBuffer buffer = pooled.getResource();
                do {
                    c = channel.read(buffer);
                    if (c > 0) {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            byte n = buffer.get();
                            switch (state) {
                                case 0: {
                                    if (n == '=') {
                                        name = builder.toString();
                                        builder.setLength(0);
                                        state = 2;
                                    } else if (n == '%' || n == '+') {
                                        state = 1;
                                        builder.append((char) n);
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 1: {
                                    if (n == '=') {
                                        name = URLDecoder.decode(builder.toString(), charset);
                                        builder.setLength(0);
                                        state = 2;
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 2: {
                                    if (n == '&') {
                                        data.add(name, builder.toString());
                                        builder.setLength(0);
                                        state = 0;
                                    } else if (n == '%' || n == '+') {
                                        state = 3;
                                        builder.append((char) n);
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 3: {
                                    if (n == '&') {
                                        data.add(name, URLDecoder.decode(builder.toString(), charset));
                                        builder.setLength(0);
                                        state = 0;
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } while (c > 0);
                if (c == -1) {
                    if (state == 2) {
                        data.add(name, builder.toString());
                    } else if (state == 3) {
                        data.add(name, URLDecoder.decode(builder.toString(), charset));
                    }
                    state = 4;
                    ioFuture.setResult(data);
                }
            } catch (IOException e) {
                ioFuture.setException(e);
                IoUtils.safeClose(channel);
                UndertowLogger.REQUEST_LOGGER.ioExceptionReadingFromChannel(e);
                exchange.endExchange();

            } finally {
                pooled.free();
            }
        }


        @Override
        public IoFuture<FormData> parse() {
            if (ioFuture == null) {
                ConcreteIoFuture<FormData> created = null;
                synchronized (this) {
                    if (ioFuture == null) {
                        ioFuture = created = new ConcreteIoFuture<FormData>();

                    }
                }
                if (created != null) {
                    StreamSourceChannel channel = exchange.getRequestChannel();
                    if (channel == null) {
                        created.setException(new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided()));
                    } else {
                        handleEvent(channel);
                        if (state != 4) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                    }
                }
            }
            return ioFuture;
        }

        @Override
        public FormData parseBlocking() throws IOException {
            if (ioFuture == null) {
                ConcreteIoFuture<FormData> created = null;
                synchronized (this) {
                    if (ioFuture == null) {
                        ioFuture = created = new ConcreteIoFuture<FormData>();

                    }
                }
                if (created != null) {
                    StreamSourceChannel channel = exchange.getRequestChannel();
                    if (channel == null) {
                        created.setException(new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided()));
                    } else {
                        while (state != 4) {
                            handleEvent(channel);
                            if (state != 4) {
                                channel.awaitReadable();
                            }
                        }
                    }
                }
            }
            return ioFuture.get();
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void setCharacterEncoding(final String encoding) {
            this.charset = encoding;
        }
    }

}
