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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
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
    private final File file;
    private final File resourceManagerRoot;

    public FileResource(final File file, final File resourceManagerRoot) {
        this.file = file;
        this.resourceManagerRoot = resourceManagerRoot;
    }

    @Override
    public Date getLastModified() {
        return new Date(file.lastModified());
    }

    @Override
    public String getLastModifiedString() {
        final Date lastModified = getLastModified();
        if(lastModified == null) {
            return null;
        }
        return DateUtils.toDateString(lastModified);
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
        for (String child : file.list()) {
            resources.add(new FileResource(new File(this.file, child), resourceManagerRoot));
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
    public void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback callback) {

        class ServerTask implements Runnable, IoCallback {

            private FileChannel fileChannel;
            private Pooled<ByteBuffer> pooled;

            @Override
            public void run() {
                if (fileChannel == null) {
                    try {
                        fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
                    } catch (FileNotFoundException e) {
                        exchange.setResponseCode(404);
                        callback.onException(exchange, sender, e);
                        return;
                    } catch (IOException e) {
                        exchange.setResponseCode(500);
                        callback.onException(exchange, sender, e);
                        return;
                    }
                    pooled = exchange.getConnection().getBufferPool().allocate();
                }
                if(pooled != null) {
                    ByteBuffer buffer = pooled.getResource();
                    try {
                        buffer.clear();
                        int res = fileChannel.read(buffer);
                        if (res == -1) {
                            //we are done
                            pooled.free();
                            callback.onComplete(exchange, sender);
                            return;
                        }
                        buffer.flip();
                        sender.send(buffer, this);
                    } catch (IOException e) {
                        onException(exchange, sender, e);
                    }
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
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                if (pooled != null) {
                    pooled.free();
                    pooled = null;
                }
                IoUtils.safeClose(fileChannel);
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
                callback.onException(exchange, sender, exception);
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
        return file.length();
    }

    @Override
    public Resource getIndexResource(final List<String> possible) {
        for (String possibility : possible) {
            File index = new File(file, possibility);
            if (index.exists()) {
                return new FileResource(index, resourceManagerRoot);
            }
        }
        return null;
    }

    @Override
    public String getCacheKey() {
        return file.toString();
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public File getResourceManagerRoot() {
        return resourceManagerRoot;
    }

    @Override
    public URL getUrl() {
        try {
            return file.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
