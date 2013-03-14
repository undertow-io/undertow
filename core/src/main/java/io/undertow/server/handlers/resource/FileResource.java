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
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

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
        //TODO: should be using async IO here as much as possible
        final FileChannel fileChannel;
        try {
            try {
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file.toFile(), FileAccess.READ_ONLY);
            } catch (FileNotFoundException e) {
                exchange.setResponseCode(404);
                exchange.endExchange();
                return;
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(file, e);
            exchange.setResponseCode(500);
            exchange.endExchange();
            return;
        }

        final StreamSinkChannel response = exchange.getResponseChannel();
        response.getCloseSetter().set(new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(fileChannel);
            }
        });


        try {
            log.tracef("Serving file %s (blocking)", fileChannel);
            Channels.transferBlocking(response, fileChannel, 0, Files.size(file));
            log.tracef("Finished serving %s, shutting down (blocking)", fileChannel);
            response.shutdownWrites();
            log.tracef("Finished serving %s, flushing (blocking)", fileChannel);
            Channels.flushBlocking(response);
            log.tracef("Finished serving %s (complete)", fileChannel);
            exchange.endExchange();
        } catch (IOException ignored) {
            log.tracef("Failed to serve %s: %s", fileChannel, ignored);
            exchange.endExchange();
            IoUtils.safeClose(response);
        } finally {
            IoUtils.safeClose(fileChannel);
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
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(file, new DirectoryStream.Filter<Path>() {
                         @Override
                         public boolean accept(Path entry) throws IOException {
                             return possible.contains(entry.getFileName().toString());
                         }
                     })) {
            Map<String, Path> found = new HashMap<>();
            for (Path entry : stream) {
                found.put(entry.getFileName().toString(), entry);
            }
            for (String possibility : possible) {//this extra loop is for ensuring order!
                if (found.containsKey(possibility)) {
                    return new FileResource(found.get(possibility));
                }
            }
        } catch (IOException e) {
            e.getStackTrace();
        }
        return null;
    }
}
