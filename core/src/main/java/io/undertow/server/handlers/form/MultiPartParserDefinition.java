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
import io.undertow.UndertowOptions;
import io.undertow.server.Connectors;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MalformedMessageException;
import io.undertow.util.MultipartParser;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author Stuart Douglas
 */
public class MultiPartParserDefinition implements FormParserFactory.ParserDefinition {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private Executor executor;

    private File tempFileLocation;

    private String defaultEncoding = "ISO-8859-1";

    private long maxIndividualFileSize = -1;

    public MultiPartParserDefinition() {
        tempFileLocation = new File(System.getProperty("java.io.tmpdir"));
    }

    public MultiPartParserDefinition(final File tempDir) {
        tempFileLocation = tempDir;
    }

    @Override
    public FormDataParser create(final HttpServerExchange exchange) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractTokenFromHeader(mimeType, "boundary");
            if(boundary == null) {
                UndertowLogger.REQUEST_LOGGER.debugf("Could not find boundary in multipart request with ContentType: %s, multipart data will not be available", mimeType);
                return null;
            }
            final MultiPartUploadHandler parser =  new MultiPartUploadHandler(exchange, boundary, maxIndividualFileSize, defaultEncoding);
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                    IoUtils.safeClose(parser);
                    nextListener.proceed();
                }
            });
            return parser;

        }
        return null;
    }

    public Executor getExecutor() {
        return executor;
    }

    public MultiPartParserDefinition setExecutor(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public File getTempFileLocation() {
        return tempFileLocation;
    }

    public MultiPartParserDefinition setTempFileLocation(File tempFileLocation) {
        this.tempFileLocation = tempFileLocation;
        return this;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public MultiPartParserDefinition setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
        return this;
    }

    public long getMaxIndividualFileSize() {
        return maxIndividualFileSize;
    }

    public void setMaxIndividualFileSize(final long maxIndividualFileSize) {
        this.maxIndividualFileSize = maxIndividualFileSize;
    }

    private final class MultiPartUploadHandler implements FormDataParser, Runnable, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final FormData data;
        private final String boundary;
        private final List<File> createdFiles = new ArrayList<File>();
        private final long maxIndividualFileSize;
        private String defaultEncoding;

        private final ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
        private String currentName;
        private String fileName;
        private File file;
        private FileChannel fileChannel;
        private HeaderMap headers;
        private HttpHandler handler;
        private long currentFileSize;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final String boundary, final long maxIndividualFileSize, final String defaultEncoding) {
            this.exchange = exchange;
            this.boundary = boundary;
            this.maxIndividualFileSize = maxIndividualFileSize;
            this.defaultEncoding = defaultEncoding;
            this.data = new FormData(exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, 1000));
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

            final MultipartParser.ParseState parser = MultipartParser.beginParse(exchange.getConnection().getBufferPool(), this, boundary.getBytes(), exchange.getRequestCharset());
            StreamSourceChannel requestChannel = exchange.getRequestChannel();
            if (requestChannel == null) {
                throw new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided());
            }
            final Pooled<ByteBuffer> resource = exchange.getConnection().getBufferPool().allocate();
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
            } catch (MalformedMessageException e) {
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
                Connectors.executeRootHandler(handler, exchange);
            } catch (Throwable e) {
                UndertowLogger.REQUEST_LOGGER.debug("Exception parsing data", e);
                exchange.setResponseCode(500);
                exchange.endExchange();
            }
        }

        @Override
        public void beginPart(final HeaderMap headers) {
            this.currentFileSize = 0;
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
        public void data(final ByteBuffer buffer) throws IOException {
            this.currentFileSize += buffer.remaining();
            if(this.maxIndividualFileSize > 0 && this.currentFileSize > this.maxIndividualFileSize) {
                throw UndertowMessages.MESSAGES.maxFileSizeExceeded(this.maxIndividualFileSize);
            }
            if (file == null) {
                while (buffer.hasRemaining()) {
                    contentBytes.write(buffer.get());
                }
            } else {
                fileChannel.write(buffer);
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
                        String cs = Headers.extractQuotedValueFromHeader(contentType, "charset");
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
            final List<File> files = new ArrayList<File>(getCreatedFiles());
            exchange.getConnection().getWorker().execute(new Runnable() {
                @Override
                public void run() {
                    for (final File file : files) {
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
