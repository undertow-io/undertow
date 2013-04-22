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

package io.undertow.server.handlers.resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.jboss.logging.Logger;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.Pooled;

/**
 * A file resource
 *
 * @author Stuart Douglas
 */
public class FileResource implements Resource {

    private static final Logger log = Logger.getLogger("io.undertow.server.resources.file");
    private final Path file;

    public FileResource(final Path file) {
        this.file = file;
    }

    @Override
    public Date getLastModified() {
        try {
            return new Date(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            return new Date(0);
        }
    }

    @Override
    public ETag getETag() {
        return null;
    }

    @Override
    public String getName() {
        return file.getFileName().toString();
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(file);
    }

    @Override
    public List<Resource> list() {
        final List<Resource> resources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
            for (Path child : stream) {
                resources.add(new FileResource(child));
            }
        } catch (IOException | DirectoryIteratorException x) {
            // IOException can never be thrown by the iteration.
            UndertowLogger.ROOT_LOGGER.warn("could not list directory", x);
        }
        return resources;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        final String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index != fileName.length() - 1) {
            return mimeMappings.getMimeType(fileName.substring(index + 1));
        }
        return null;
    }

    @Override
    public void serve(final HttpServerExchange exchange) {

        class ServerTask implements Runnable, IoCallback {

            private FileChannel fileChannel;
            private Pooled<ByteBuffer> pooled;
            private Sender sender;

            @Override
            public void run() {
                if (fileChannel == null) {
                    try {
                        fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file.toFile(), FileAccess.READ_ONLY);
                    } catch (FileNotFoundException e) {
                        exchange.setResponseCode(404);
                        return;
                    } catch (IOException e) {
                        exchange.setResponseCode(500);
                        return;
                    }
                    pooled = exchange.getConnection().getBufferPool().allocate();
                    sender = exchange.getResponseSender();
                }
                ByteBuffer buffer = pooled.getResource();
                try {
                    int res = fileChannel.read(buffer);
                    if (res == -1) {
                        //we are done, just return
                        sender.close();
                        return;
                    }
                    buffer.flip();
                    sender.send(buffer, this);
                } catch (IOException e) {
                    onException(exchange, sender, e);
                }

            }

            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                } else {
                    run();
                }
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                if (pooled != null) {
                    pooled.free();
                }
                IoUtils.safeClose(fileChannel);
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
                exchange.endExchange();
            }
        }

        ServerTask serveTask = new ServerTask();
        if (exchange.isInIoThread()) {
            exchange.dispatch(serveTask);
        } else {
            serveTask.run();
        }
    }

    @Override
    public Long getContentLength() {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public Resource getIndexResource(final List<String> possible) {
        for (String possibility : possible) {
            Path index = file.resolve(possibility);
            if (Files.exists(index)) {
                return new FileResource(index);
            }
        }
        return null;
    }

    @Override
    public String getCacheKey() {
        return file.toString();
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public URL getUrl() {
        try {
            return file.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
