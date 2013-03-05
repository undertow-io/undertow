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

package io.undertow.server.handlers.resource.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
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

    private final File file;

    public FileResource(final File file) {
        this.file = file;
    }

    @Override
    public Date getLastModified() {
        return new Date(file.lastModified());
    }

    @Override
    public ETag getETag() {
        return null;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public List<Resource> list() {
        final List<Resource> resources = new ArrayList<Resource>();
        for (String f : file.list()) {
            final File child = new File(file, f);
            resources.add(new FileResource(child));
        }
        return resources;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        final String fileName = file.getName();
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
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
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
            Channels.transferBlocking(response, fileChannel, 0, file.length());
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
        return file.length();
    }
}
