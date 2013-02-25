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
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MultipartParser;
import io.undertow.util.WorkerDispatcher;
import org.xnio.FileAccess;
import org.xnio.IoFuture;
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
    public void handleRequest(final HttpServerExchange exchange) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractTokenFromHeader(mimeType, "boundary");
            final MultiPartUploadHandler multiPartUploadHandler = new MultiPartUploadHandler(exchange, boundary, defaultEncoding);
            exchange.putAttachment(FormDataParser.ATTACHMENT_KEY, multiPartUploadHandler);
            HttpHandlers.executeHandler(next, exchange);
        } else {
            HttpHandlers.executeHandler(next, exchange);
        }
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

    public File getTempFileLocation() {
        return tempFileLocation;
    }

    public void setTempFileLocation(File tempFileLocation) {
        this.tempFileLocation = tempFileLocation;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    private final class MultiPartUploadHandler implements FormDataParser, Runnable, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final FormData data = new FormData();
        private final String boundary;
        private final List<File> createdFiles = new ArrayList<File>();
        private volatile ConcreteIoFuture<FormData> ioFuture;
        private String defaultEncoding;

        //0=form data
        int currentType = 0;
        private final ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
        private String currentName;
        private String fileName;
        private File file;
        private FileChannel fileChannel;
        private HeaderMap headers;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final String boundary, final String defaultEncoding) {
            this.exchange = exchange;
            this.boundary = boundary;
            this.defaultEncoding = defaultEncoding;
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
                    //we need to delegate to a thread pool
                    //as we parse with blocking operations
                    if(executor == null) {
                        WorkerDispatcher.dispatch(exchange, this);
                    } else {
                        executor.execute(this);
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
                        UndertowLogger.REQUEST_LOGGER.connectionTerminatedReadingMultiPartData();
                        exchange.endExchange();
                        return;
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
                            ioFuture.setException(e);
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
                    ioFuture.setException(e);
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
                    ioFuture.setException(e);
                    throw new RuntimeException(e);
                }
            } else {


                try {
                    String charset = defaultEncoding;
                    String contentType = headers.getFirst(Headers.CONTENT_TYPE);
                    if(contentType != null) {
                        String cs = Headers.extractTokenFromHeader(contentType, "charset");
                        if(cs != null) {
                            charset = cs;
                        }
                    }

                    data.add(currentName, new String(contentBytes.toByteArray(),charset), headers);
                } catch (UnsupportedEncodingException e) {
                    ioFuture.setException(e);
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
            WorkerDispatcher.dispatch(exchange, new Runnable() {
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
