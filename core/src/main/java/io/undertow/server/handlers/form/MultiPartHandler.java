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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
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
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.MultipartParser;
import io.undertow.util.WorkerDispatcher;
import org.xnio.FileAccess;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * TODO: upload limits
 *
 * @author Stuart Douglas
 */
public class MultiPartHandler implements HttpHandler {

    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private volatile Executor executor;

    private volatile File tempFileLocation = new File(System.getProperty("java.io.tmpdir"));

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            String boundary = Headers.extractTokenFromHeader(mimeType, "boundary");
            final MultiPartUploadHandler multiPartUploadHandler = new MultiPartUploadHandler(exchange, completionHandler, boundary);
            exchange.putAttachment(FormDataParser.ATTACHMENT_KEY, multiPartUploadHandler);

            HttpHandlers.executeHandler(next, exchange, new HttpCompletionHandler() {
                @Override
                public void handleComplete() {
                    try {
                        completionHandler.handleComplete();
                    } finally {
                        for (final File file : multiPartUploadHandler.getCreatedFiles()) {
                            if (file.exists()) {
                                if (!file.delete()) {
                                    UndertowLogger.REQUEST_LOGGER.cannotRemoveUploadedFile(file);
                                }
                            }
                        }
                    }

                }
            });
        } else {
            HttpHandlers.executeHandler(next, exchange, completionHandler);
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

    private final class MultiPartUploadHandler implements FormDataParser, Runnable, MultipartParser.PartHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final FormData data = new FormData();
        private final String boundary;
        private final List<File> createdFiles = new ArrayList<File>();
        private volatile ConcreteIoFuture<FormData> ioFuture;

        //0=form data
        int currentType = 0;
        private final StringBuilder builder = new StringBuilder();
        private String currentName;
        private String fileName;
        private File file;
        private FileChannel fileChannel;
        private HeaderMap headers;


        private MultiPartUploadHandler(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final String boundary) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.boundary = boundary;
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
                        IoUtils.safeClose(requestChannel);
                        UndertowLogger.REQUEST_LOGGER.connectionTerminatedReadingMultiPartData();
                        completionHandler.handleComplete();
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
                builder.ensureCapacity(builder.length() + buffer.remaining());
                while (buffer.hasRemaining()) {
                    builder.append((char) buffer.get());
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
                data.add(currentName, builder.toString(), headers);
                builder.setLength(0);
            }
        }


        public List<File> getCreatedFiles() {
            return createdFiles;
        }

    }

}
