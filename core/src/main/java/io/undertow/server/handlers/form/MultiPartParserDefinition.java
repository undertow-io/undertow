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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.IoUtils;
import io.undertow.util.MalformedMessageException;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;
import io.undertow.util.UndertowOptions;

/**
 * @author Stuart Douglas
 */
public class MultiPartParserDefinition implements FormParserFactory.ParserDefinition<MultiPartParserDefinition> {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private Executor executor;

    private Path tempFileLocation;

    private String defaultEncoding = StandardCharsets.ISO_8859_1.displayName();

    private long maxIndividualFileSize = -1;

    private long fileSizeThreshold;

    public MultiPartParserDefinition() {
        tempFileLocation = Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public MultiPartParserDefinition(final Path tempDir) {
        tempFileLocation = tempDir;
    }

    @Override
    public FormDataParser create(final HttpServerExchange exchange) {
        String mimeType = exchange.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = HttpHeaderNames.extractQuotedValueFromHeader(mimeType, "boundary");
            if (boundary == null) {
                UndertowLogger.REQUEST_LOGGER.debugf("Could not find boundary in multipart request with ContentType: %s, multipart data will not be available", mimeType);
                return null;
            }
            final MultiPartUploadHandler parser = new MultiPartUploadHandler(exchange, boundary, maxIndividualFileSize, fileSizeThreshold, defaultEncoding);
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                    IoUtils.safeClose(parser);
                    nextListener.proceed();
                }
            });
            Long sizeLimit = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE);
            if (sizeLimit != null) {
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

    public void setFileSizeThreshold(long fileSizeThreshold) {
        this.fileSizeThreshold = fileSizeThreshold;
    }

    private final class MultiPartUploadHandler implements FormDataParser, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final FormData data;
        private final List<Path> createdFiles = new ArrayList<>();
        private final long maxIndividualFileSize;
        private final long fileSizeThreshold;
        private String defaultEncoding;

        private final ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
        private String currentName;
        private String fileName;
        private Path file;
        private FileChannel fileChannel;
        private HttpHeaders headers;
        private HttpHandler handler;
        private long currentFileSize;
        private final MultipartParser.ParseState parser;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final String boundary, final long maxIndividualFileSize, final long fileSizeThreshold, final String defaultEncoding) {
            this.exchange = exchange;
            this.maxIndividualFileSize = maxIndividualFileSize;
            this.defaultEncoding = defaultEncoding;
            this.fileSizeThreshold = fileSizeThreshold;
            this.data = new FormData(exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, 1000));
            String charset = defaultEncoding;
            String contentType = exchange.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE);
            if (contentType != null) {
                String value = HttpHeaderNames.extractQuotedValueFromHeader(contentType, "charset");
                if (value != null) {
                    charset = value;
                }
            }
            this.parser = MultipartParser.beginParse(exchange, this, boundary.getBytes(StandardCharsets.US_ASCII), charset);

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

            NonBlockingParseTask task;
            if (executor == null) {
                task = new NonBlockingParseTask(exchange.getConnection().getWorker());
            } else {
                task = new NonBlockingParseTask(executor);
            }
            exchange.readAsync(task);
        }

        @Override
        public FormData parseBlocking() throws IOException {
            final FormData existing = exchange.getAttachment(FORM_DATA);
            if (existing != null) {
                return existing;
            }
            try {
                while (true) {
                    ByteBuf buf = exchange.readBlocking();
                    try {
                        if (buf == null) {
                            if (parser.isComplete()) {
                                break;
                            } else {
                                throw UndertowMessages.MESSAGES.connectionTerminatedReadingMultiPartData();
                            }
                        } else {
                            parser.parse(buf);
                        }
                    } finally {
                        if(buf != null) {
                            buf.release();
                        }
                    }
                }
                exchange.putAttachment(FORM_DATA, data);
            } catch (MalformedMessageException e) {
                throw new IOException(e);
            }
            return exchange.getAttachment(FORM_DATA);
        }

        @Override
        public void beginPart(final HttpHeaders headers) {
            this.currentFileSize = 0;
            this.headers = headers;
            final String disposition = headers.get(HttpHeaderNames.CONTENT_DISPOSITION);
            if (disposition != null) {
                if (disposition.startsWith("form-data")) {
                    currentName = HttpHeaderNames.extractQuotedValueFromHeader(disposition, "name");
                    fileName = HttpHeaderNames.extractQuotedValueFromHeaderWithEncoding(disposition, "filename");
                    if (fileName != null && fileSizeThreshold == 0) {
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
        public void data(final ByteBuf buffer) throws IOException {
            this.currentFileSize += buffer.readableBytes();
            if (this.maxIndividualFileSize > 0 && this.currentFileSize > this.maxIndividualFileSize) {
                throw UndertowMessages.MESSAGES.maxFileSizeExceeded(this.maxIndividualFileSize);
            }
            if (file == null && fileName != null && fileSizeThreshold < this.currentFileSize) {
                try {
                    if (tempFileLocation != null) {
                        file = Files.createTempFile(tempFileLocation, "undertow", "upload");
                    } else {
                        file = Files.createTempFile("undertow", "upload");
                    }
                    createdFiles.add(file);

                    FileOutputStream fileOutputStream = new FileOutputStream(file.toFile());
                    contentBytes.writeTo(fileOutputStream);

                    fileChannel = fileOutputStream.getChannel();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (file == null) {
                while (buffer.isReadable()) {
                    contentBytes.write(0xFF & buffer.readByte());
                }
            } else {
                fileChannel.write(buffer.nioBuffer());
            }
        }

        @Override
        public void endPart() {
            if (file != null) {
                data.add(currentName, file, fileName, headers);
                file = null;
                contentBytes.reset();
                try {
                    fileChannel.close();
                    fileChannel = null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (fileName != null) {
                data.add(currentName, Arrays.copyOf(contentBytes.toByteArray(), contentBytes.size()), fileName, headers);
                contentBytes.reset();
            } else {


                try {
                    String charset = defaultEncoding;
                    String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
                    if (contentType != null) {
                        String cs = HttpHeaderNames.extractQuotedValueFromHeader(contentType, "charset");
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
            IoUtils.safeClose(fileChannel);
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

        private final class NonBlockingParseTask implements IoCallback<ByteBuf>, Runnable {

            private final Executor executor;
            private ByteBuf buffer;

            private NonBlockingParseTask(Executor executor) {
                this.executor = executor;
            }


            @Override
            public void onComplete(HttpServerExchange exchange, ByteBuf context) {
                buffer = context;
                executor.execute(this);
            }

            @Override
            public void onException(HttpServerExchange exchange, ByteBuf context, IOException exception) {
                UndertowLogger.REQUEST_IO_LOGGER.debug("Exception parsing data", exception);
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                exchange.endExchange();
            }

            @Override
            public void run() {
                try {
                    if (buffer == null) {
                        if (parser.isComplete()) {
                            exchange.putAttachment(FORM_DATA, data);
                            exchange.dispatch(SameThreadExecutor.INSTANCE, handler);
                        } else {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(UndertowMessages.MESSAGES.connectionTerminatedReadingMultiPartData());
                            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                            exchange.endExchange();
                        }
                    } else {
                        parser.parse(buffer);
                        exchange.readAsync(this);
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    exchange.endExchange();
                }
            }
        }
    }


    public static class FileTooLargeException extends IOException {

        public FileTooLargeException() {
            super();
        }

        public FileTooLargeException(String message) {
            super(message);
        }

        public FileTooLargeException(String message, Throwable cause) {
            super(message, cause);
        }

        public FileTooLargeException(Throwable cause) {
            super(cause);
        }
    }

}
