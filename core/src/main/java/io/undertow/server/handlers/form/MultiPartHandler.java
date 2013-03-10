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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MultipartParser;
import org.xnio.FileAccess;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * TODO: upload limits
 *
 * @author Stuart Douglas
 */
public class MultiPartHandler implements HttpHandler {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private Executor executor;

    private File tempFileLocation = new File(System.getProperty("java.io.tmpdir"));

    private String defaultEncoding = "UTF-8";

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractTokenFromHeader(mimeType, "boundary");
            final MultiPartUploadHandler multiPartUploadHandler = new MultiPartUploadHandler(exchange, boundary, defaultEncoding);
            exchange.putAttachment(FormDataParser.ATTACHMENT_KEY, multiPartUploadHandler);
        }
        next.handleRequest(exchange);
    }


    public HttpHandler getNext() {
        return next;
    }

    public MultiPartHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public Executor getExecutor() {
        return executor;
    }

    public MultiPartHandler setExecutor(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public File getTempFileLocation() {
        return tempFileLocation;
    }

    public MultiPartHandler setTempFileLocation(File tempFileLocation) {
        this.tempFileLocation = tempFileLocation;
        return this;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public MultiPartHandler setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
        return this;
    }

    private final class MultiPartUploadHandler implements FormDataParser, Runnable, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final FormData data = new FormData();
        private final String boundary;
        private final List<File> createdFiles = new ArrayList<File>();
        private String defaultEncoding;

        //0=form data
        int currentType = 0;
        private final ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
        private String currentName;
        private String fileName;
        private File file;
        private FileChannel fileChannel;
        private HeaderMap headers;
        private HttpHandler handler;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final String boundary, final String defaultEncoding) {
            this.exchange = exchange;
            this.boundary = boundary;
            this.defaultEncoding = defaultEncoding;
        }


        @Override
        public void parse(final HttpHandler handler) throws Exception {
            if (exchange.getAttachment(FORM_DATA) != null) {
                handler.handleRequest(exchange);
                return;
            }
            this.handler = handler;
            //we need to delegate to a thread pool
            //as we parse with blocking operations
            if (executor == null) {
                exchange.dispatch(this);
            } else {
                exchange.dispatch(executor, this);
            }
        }

        @Override
        public FormData parseBlocking() throws IOException {
            final FormData existing = exchange.getAttachment(FORM_DATA);
            if (existing != null) {
                return existing;
            }

            final MultipartParser.ParseState parser = MultipartParser.beginParse(exchange.getConnection().getBufferPool(), this, boundary.getBytes());
            final Pooled<ByteBuffer> resource = exchange.getConnection().getBufferPool().allocate();
            StreamSourceChannel requestChannel = exchange.getRequestChannel();
            if (requestChannel == null) {
                throw new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided());
            }
            final ByteBuffer buf = resource.getResource();
            try {
                while (!parser.isComplete()) {
                    buf.clear();
                    requestChannel.awaitReadable();
                    int c = requestChannel.read(buf);
                    buf.flip();
                    if (c == -1) {
                        throw UndertowMessages.MESSAGES.connectionTerminatedReadingMultiPartData();
                    } else if (c != 0) {
                        parser.parse(buf);
                    }
                }
                exchange.putAttachment(FORM_DATA, data);
            } catch (MultipartParser.MalformedMessageException e) {
                throw new IOException(e);
            } finally {
                resource.free();
            }
            return exchange.getAttachment(FORM_DATA);
        }

        @Override
        public void run() {
            try {
                parseBlocking();
                HttpHandlers.executeRootHandler(handler, exchange, false);
            } catch (Throwable e) {
                UndertowLogger.REQUEST_LOGGER.debug("Exception parsing data", e);
                exchange.setResponseCode(500);
                exchange.endExchange();
            }
        }

        @Override
        public void beginPart(final HeaderMap headers) {
            this.headers = headers;
            final String disposition = headers.getFirst(Headers.CONTENT_DISPOSITION);
            if (disposition != null) {
                if (disposition.startsWith("form-data")) {
                    currentName = Headers.extractQuotedValueFromHeader(disposition, "name");
                    fileName = Headers.extractQuotedValueFromHeader(disposition, "filename");
                    if (fileName != null) {
                        try {
                            file = File.createTempFile("undertow", "upload", tempFileLocation);
                            createdFiles.add(file);
                            fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_WRITE);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        @Override
        public void data(final ByteBuffer buffer) {
            if (file == null) {
                while (buffer.hasRemaining()) {
                    contentBytes.write(buffer.get());
                }
            } else {
                try {
                    fileChannel.write(buffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void endPart() {
            if (file != null) {
                data.add(currentName, file, fileName, headers);
                file = null;
                try {
                    fileChannel.close();
                    fileChannel = null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {


                try {
                    String charset = defaultEncoding;
                    String contentType = headers.getFirst(Headers.CONTENT_TYPE);
                    if (contentType != null) {
                        String cs = Headers.extractTokenFromHeader(contentType, "charset");
                        if (cs != null) {
                            charset = cs;
                        }
                    }

                    data.add(currentName, new String(contentBytes.toByteArray(), charset), headers);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                contentBytes.reset();
            }
        }


        public List<File> getCreatedFiles() {
            return createdFiles;
        }

        @Override
        public void close() throws IOException {
            //we have to dispatch this, as it may result in file IO
            exchange.dispatch(new Runnable() {
                @Override
                public void run() {
                    for (final File file : getCreatedFiles()) {
                        if (file.exists()) {
                            if (!file.delete()) {
                                UndertowLogger.REQUEST_LOGGER.cannotRemoveUploadedFile(file);
                            }
                        }
                    }
                }
            });

        }

        @Override
        public void setCharacterEncoding(final String encoding) {
            this.defaultEncoding = encoding;
        }
    }

}
