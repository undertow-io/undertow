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

package io.undertow.server.handlers.form;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MalformedMessageException;
import io.undertow.util.MultipartParser;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author Stuart Douglas
 */
public class MultiPartParserDefinition implements FormParserFactory.ParserDefinition<MultiPartParserDefinition> {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private Executor executor;

    private Path tempFileLocation;

    private String defaultEncoding = StandardCharsets.ISO_8859_1.displayName();

    private long maxIndividualFileSize = -1;

    public MultiPartParserDefinition() {
        tempFileLocation = Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public MultiPartParserDefinition(final Path tempDir) {
        tempFileLocation = tempDir;
    }

    @Override
    public FormDataParser create(final HttpServerExchange exchange) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractQuotedValueFromHeader(mimeType, "boundary");
            if (boundary == null) {
                UndertowLogger.REQUEST_LOGGER.debugf("Could not find boundary in multipart request with ContentType: %s, multipart data will not be available", mimeType);
                return null;
            }
            final MultiPartUploadHandler parser = new MultiPartUploadHandler(exchange, boundary, maxIndividualFileSize, defaultEncoding);
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                    IoUtils.safeClose(parser);
                    nextListener.proceed();
                }
            });
            Long sizeLimit = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE);
            if(sizeLimit != null) {
                exchange.setMaxEntitySize(sizeLimit);
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Created multipart parser for %s", exchange);

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

    public Path getTempFileLocation() {
        return tempFileLocation;
    }

    public MultiPartParserDefinition setTempFileLocation(Path tempFileLocation) {
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

    private final class MultiPartUploadHandler implements FormDataParser, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final FormData data;
        private final String boundary;
        private final List<Path> createdFiles = new ArrayList<>();
        private final long maxIndividualFileSize;
        private String defaultEncoding;

        private final ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
        private String currentName;
        private String fileName;
        private Path file;
        private FileChannel fileChannel;
        private HeaderMap headers;
        private HttpHandler handler;
        private long currentFileSize;
        private final MultipartParser.ParseState parser;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final String boundary, final long maxIndividualFileSize, final String defaultEncoding) {
            this.exchange = exchange;
            this.boundary = boundary;
            this.maxIndividualFileSize = maxIndividualFileSize;
            this.defaultEncoding = defaultEncoding;
            this.data = new FormData(exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, 1000));
            String charset = defaultEncoding;
            String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            if (contentType != null) {
                String value = Headers.extractQuotedValueFromHeader(contentType, "charset");
                if (value != null) {
                    charset = value;
                }
            }
           this.parser = MultipartParser.beginParse(exchange.getConnection().getByteBufferPool(), this, boundary.getBytes(StandardCharsets.US_ASCII), charset);

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

            StreamSourceChannel requestChannel = exchange.getRequestChannel();
            if (requestChannel == null) {
                throw new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided());
            }
            if (executor == null) {
                exchange.dispatch(new NonBlockingParseTask(exchange.getConnection().getWorker(), requestChannel));
            } else {
                exchange.dispatch(executor, new NonBlockingParseTask(executor, requestChannel));
            }
        }

        @Override
        public FormData parseBlocking() throws IOException {
            final FormData existing = exchange.getAttachment(FORM_DATA);
            if (existing != null) {
                return existing;
            }
            InputStream inputStream = exchange.getInputStream();
            if (inputStream == null) {
                throw new IOException(UndertowMessages.MESSAGES.requestChannelAlreadyProvided());
            }
            try (PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate()){
                ByteBuffer buf = pooled.getBuffer();
                while (true) {
                    buf.clear();
                    int c = inputStream.read(buf.array(), buf.arrayOffset(), buf.remaining());
                    if (c == -1) {
                        if (parser.isComplete()) {
                            break;
                        } else {
                            throw UndertowMessages.MESSAGES.connectionTerminatedReadingMultiPartData();
                        }
                    } else if (c != 0) {
                        buf.limit(c);
                        parser.parse(buf);
                    }
                }
                exchange.putAttachment(FORM_DATA, data);
            } catch (MalformedMessageException e) {
                throw new IOException(e);
            }
            return exchange.getAttachment(FORM_DATA);
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
                            if (tempFileLocation != null) {
                                file = Files.createTempFile(tempFileLocation, "undertow", "upload");
                            } else {
                                file = Files.createTempFile("undertow", "upload");
                            }
                            createdFiles.add(file);
                            fileChannel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
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
            if (this.maxIndividualFileSize > 0 && this.currentFileSize > this.maxIndividualFileSize) {
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


        public List<Path> getCreatedFiles() {
            return createdFiles;
        }

        @Override
        public void close() throws IOException {
            //we have to dispatch this, as it may result in file IO
            final List<Path> files = new ArrayList<>(getCreatedFiles());
            exchange.getConnection().getWorker().execute(new Runnable() {
                @Override
                public void run() {
                    for (final Path file : files) {
                        if (Files.exists(file)) {
                            try {
                                Files.delete(file);
                            } catch (NoSuchFileException e) { // ignore
                            } catch (IOException e) {
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
            parser.setCharacterEncoding(encoding);
        }

        private final class NonBlockingParseTask implements Runnable {

            private final Executor executor;
            private final StreamSourceChannel requestChannel;

            private NonBlockingParseTask(Executor executor, StreamSourceChannel requestChannel) {
                this.executor = executor;
                this.requestChannel = requestChannel;
            }

            @Override
            public void run() {
                try {
                    final FormData existing = exchange.getAttachment(FORM_DATA);
                    if (existing != null) {
                        exchange.dispatch(SameThreadExecutor.INSTANCE, handler);
                        return;
                    }
                    PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                    try {
                        while (true) {
                            int c = requestChannel.read(pooled.getBuffer());
                            if(c == 0) {
                                requestChannel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                                    @Override
                                    public void handleEvent(StreamSourceChannel channel) {
                                        channel.suspendReads();
                                        executor.execute(NonBlockingParseTask.this);
                                    }
                                });
                                requestChannel.resumeReads();
                                return;
                            } else if (c == -1) {
                                if (parser.isComplete()) {
                                    exchange.putAttachment(FORM_DATA, data);
                                    exchange.dispatch(SameThreadExecutor.INSTANCE, handler);
                                } else {
                                    UndertowLogger.REQUEST_IO_LOGGER.ioException(UndertowMessages.MESSAGES.connectionTerminatedReadingMultiPartData());
                                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                                    exchange.endExchange();
                                }
                                return;
                            } else {
                                pooled.getBuffer().flip();
                                parser.parse(pooled.getBuffer());
                                pooled.getBuffer().compact();
                            }
                        }
                    } catch (MalformedMessageException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        exchange.endExchange();
                    } finally {
                        pooled.close();
                    }

                } catch (Throwable e) {
                    UndertowLogger.REQUEST_IO_LOGGER.debug("Exception parsing data", e);
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    exchange.endExchange();
                }
            }
        }
     }



}
