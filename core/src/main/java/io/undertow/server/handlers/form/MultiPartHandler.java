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
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.ImmediateIoFuture;
import io.undertow.util.MultipartParser;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public class MultiPartHandler implements HttpHandler {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private volatile Executor executor;

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractTokenFromHeader(mimeType, "boundary");
            exchange.putAttachment(FormDataParser.ATTACHMENT_KEY, new MultiPartUploadHandler(exchange, completionHandler, boundary));
        }
        HttpHandlers.executeHandler(next, exchange, completionHandler);
    }


    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    private final class MultiPartUploadHandler implements FormDataParser, Runnable, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final FormData data = new FormData();
        private final String boundary;
        private volatile ImmediateIoFuture<FormData> ioFuture;

        //0=form data
        int currentType = 0;
        private final StringBuilder builder = new StringBuilder();
        private String currentName;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final String boundary) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.boundary = boundary;
        }


        @Override
        public IoFuture<FormData> parse() {
            if (ioFuture == null) {
                ImmediateIoFuture<FormData> created = null;
                synchronized (this) {
                    if (ioFuture == null) {
                        ioFuture = created = new ImmediateIoFuture<FormData>();

                    }
                }
                if (created != null) {
                    //we need to delegate to a thread pool
                    //as we parse with blocking operations
                    (executor == null ? exchange.getConnection().getWorker() : executor).execute(this);
                }
            }
            return ioFuture;
        }

        @Override
        public FormData parseBlocking() throws IOException {
            if (ioFuture == null) {
                ImmediateIoFuture<FormData> created = null;
                synchronized (this) {
                    if (ioFuture == null) {
                        ioFuture = created = new ImmediateIoFuture<FormData>();

                    }
                }
                if (created != null) {
                    run();
                }
            }
            return ioFuture.get();
        }

        @Override
        public void run() {

            final MultipartParser.ParseState parser = MultipartParser.beginParse(exchange.getConnection().getBufferPool(), this, boundary.getBytes());
            final Pooled<ByteBuffer> resource = exchange.getConnection().getBufferPool().allocate();
            StreamSourceChannel requestChannel = exchange.getRequestChannel();
            if (requestChannel == null) {
                ioFuture.setException(new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided()));
                return;
            }
            final ByteBuffer buf = resource.getResource();
            try {
                while (!parser.isComplete()) {
                    buf.clear();
                    requestChannel.awaitReadable();
                    int c = requestChannel.read(buf);
                    buf.flip();
                    if (c == -1) {
                        IoUtils.safeClose(requestChannel);
                        UndertowLogger.REQUEST_LOGGER.connectionTerminatedReadingMultiPartData();
                        completionHandler.handleComplete();
                    } else if (c != 0) {
                        parser.parse(buf);
                    }
                }
            } catch (IOException e) {
                ioFuture.setException(e);
            } catch (MultipartParser.MalformedMessageException e) {
                ioFuture.setException(new IOException(e));
            } finally {
                resource.free();
                ioFuture.setResult(data);
            }
        }

        @Override
        public void beginPart(final HeaderMap headers) {
            final String disposition = headers.getFirst(Headers.CONTENT_DISPOSITION);
            if (disposition != null) {
                if(disposition.startsWith("form-data")) {
                    currentName = Headers.extractQuotedValueFromHeader(disposition, "name");
                }
            }
        }

        @Override
        public void data(final ByteBuffer buffer) {
            while (buffer.hasRemaining()) {
                builder.append((char)buffer.get());
            }
        }

        @Override
        public void endPart() {
            data.put(currentName, builder.toString());
            builder.setLength(0);
        }
    }

}
