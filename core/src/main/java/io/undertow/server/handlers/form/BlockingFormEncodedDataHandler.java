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
import java.io.InputStream;
import java.net.URLDecoder;

import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.FailedIoFuture;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.IoUtils;

/**
 * Handler for submitted form data. This handler takes effect for any request that has a mime type
 * of application/x-www-form-urlencoded. A form data parser will be attached to the exchange that
 * parses the request using blocking methods.
 * <p/>
 * This handler should be present before any servlet handlers, to enable servlet to use parsed form data.
 * <p/>
 * Note that this handler does not handle multipart requests.
 *
 * @author Stuart Douglas
 */
public class BlockingFormEncodedDataHandler implements BlockingHttpHandler {

    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    private volatile BlockingHttpHandler next = ResponseCodeHandler.HANDLE_404;


    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        String mimeType = exchange.getExchange().getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.equals(APPLICATION_X_WWW_FORM_URLENCODED)) {
            exchange.getExchange().putAttachment(FormDataParser.ATTACHMENT_KEY, new BlockingFormDataParser(exchange));
        }
        next.handleRequest(exchange);
    }

    private static final class BlockingFormDataParser implements FormDataParser {

        private final BlockingHttpServerExchange exchange;
        private volatile IoFuture<FormData> ioFuture;


        private BlockingFormDataParser(final BlockingHttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public synchronized IoFuture<FormData> parse() {
            if (ioFuture == null) {
                synchronized (this) {
                    if (ioFuture == null) {
                        FormData data = new FormData();
                        byte[] buff = new byte[1024];
                        final InputStream in = exchange.getInputStream();

                        int state = 0;
                        String name = null;
                        final StringBuilder builder = new StringBuilder();

                        try {
                            int read = 0;
                            while ((read = in.read(buff)) > 0) {
                                for (int i = 0; i < read; ++i) {
                                    byte n = buff[i];
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
                                                name = URLDecoder.decode(builder.toString(), "UTF-8");
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
                                                data.add(name, URLDecoder.decode(builder.toString(), "UTF-8"));
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
                            if(state == 2) {
                                data.add(name, builder.toString());
                            } else if(state == 3) {
                                data.add(name, URLDecoder.decode(builder.toString(), "UTF-8"));
                            }
                            ioFuture = new FinishedIoFuture<FormData>(data);
                        } catch (IOException e) {
                            IoUtils.safeClose(in);
                            ioFuture = new FailedIoFuture<FormData>(e);
                        } finally {
                            IoUtils.safeClose(in);
                        }
                    }
                }
            }
            return ioFuture;
        }
    }

    public BlockingHttpHandler getNext() {
        return next;
    }

    public void setNext(final BlockingHttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }
}
