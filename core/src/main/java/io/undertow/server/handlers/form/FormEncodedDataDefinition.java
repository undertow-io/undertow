/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.form;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.URLUtils;
import io.undertow.util.UndertowOptions;

/**
 * Parser definition for form encoded data. This handler takes effect for any request that has a mime type
 * of application/x-www-form-urlencoded. The handler attaches a {@link FormDataParser} to the chain
 * that can parse the underlying form data asynchronously.
 *
 * @author Stuart Douglas
 */
public class FormEncodedDataDefinition implements FormParserFactory.ParserDefinition<FormEncodedDataDefinition> {

    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private String defaultEncoding = "ISO-8859-1";
    private boolean forceCreation = false; //if the parser should be created even if the correct headers are missing

    public FormEncodedDataDefinition() {
    }

    @Override
    public FormDataParser create(final HttpServerExchange exchange) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (forceCreation || (mimeType != null && mimeType.startsWith(APPLICATION_X_WWW_FORM_URLENCODED))) {

            String charset = defaultEncoding;
            String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            if (contentType != null) {
                String cs = Headers.extractQuotedValueFromHeader(contentType, "charset");
                if (cs != null) {
                    charset = cs;
                }
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Created form encoded parser for %s", exchange);
            return new FormEncodedDataParser(charset, exchange);
        }
        return null;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public boolean isForceCreation() {
        return forceCreation;
    }

    public FormEncodedDataDefinition setForceCreation(boolean forceCreation) {
        this.forceCreation = forceCreation;
        return this;
    }

    public FormEncodedDataDefinition setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
        return this;
    }

    private static final class FormEncodedDataParser implements IoCallback<ByteBuf>, FormDataParser {

        private final HttpServerExchange exchange;
        private final FormData data;
        private final StringBuilder builder = new StringBuilder();
        private String name = null;
        private String charset;
        private HttpHandler handler;

        //0= parsing name
        //1=parsing name, decode required
        //2=parsing value
        //3=parsing value, decode required
        //4=finished
        private int state = 0;

        private FormEncodedDataParser(final String charset, final HttpServerExchange exchange) {
            this.exchange = exchange;
            this.charset = charset;
            this.data = new FormData(exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, 1000));
        }

        private void doParse(ByteBuf buffer) {
            try {
                if (buffer != null) {
                    while (buffer.isReadable()) {
                        byte n = buffer.readByte();
                        switch (state) {
                            case 0: {
                                if (n == '=') {
                                    name = builder.toString();
                                    builder.setLength(0);
                                    state = 2;
                                } else if (n == '&') {
                                    data.add(builder.toString(), "");
                                    builder.setLength(0);
                                    state = 0;
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
                                    name = URLUtils.decode(builder.toString(), charset, true, new StringBuilder());
                                    builder.setLength(0);
                                    state = 2;
                                } else if (n == '&') {
                                    data.add(URLUtils.decode(builder.toString(), charset, true, new StringBuilder()), "");
                                    builder.setLength(0);
                                    state = 0;
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
                                    data.add(name, URLUtils.decode(builder.toString(), charset, true, new StringBuilder()));
                                    builder.setLength(0);
                                    state = 0;
                                } else {
                                    builder.append((char) n);
                                }
                                break;
                            }
                        }
                    }
                } else {
                    if (state == 2) {
                        data.add(name, builder.toString());
                    } else if (state == 3) {
                        data.add(name, URLUtils.decode(builder.toString(), charset, true, new StringBuilder()));
                    } else if (builder.length() > 0) {
                        if (state == 1) {
                            data.add(URLUtils.decode(builder.toString(), charset, true, new StringBuilder()), "");
                        } else {
                            data.add(builder.toString(), "");
                        }
                    }
                    state = 4;
                    exchange.putAttachment(FORM_DATA, data);
                }
            } finally {
                if (buffer != null) {
                    buffer.release();
                }
            }
        }


        @Override
        public void parse(HttpHandler handler) throws Exception {
            if (exchange.getAttachment(FORM_DATA) != null) {
                handler.handleRequest(exchange);
                return;
            }
            this.handler = handler;
            exchange.readAsync(this);

        }

        @Override
        public FormData parseBlocking() throws IOException {
            final FormData existing = exchange.getAttachment(FORM_DATA);
            if (existing != null) {
                return existing;
            }

            while (state != 4) {
                doParse(exchange.readBlocking());
            }
            return data;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void setCharacterEncoding(final String encoding) {
            this.charset = encoding;
        }

        @Override
        public void onComplete(HttpServerExchange exchange, ByteBuf buffer) {
            doParse(buffer);
            if (state != 4) {
                exchange.readAsync(this);
            } else {
                exchange.dispatch(SameThreadExecutor.INSTANCE, handler);
            }
        }

        @Override
        public void onException(HttpServerExchange exchange, ByteBuf context, IOException exception) {
            UndertowLogger.REQUEST_IO_LOGGER.ioExceptionReadingFromChannel(exception);
            exchange.endExchange();
        }
    }

}
