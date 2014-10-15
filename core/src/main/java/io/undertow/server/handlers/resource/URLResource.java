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

package io.undertow.server.handlers.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import org.xnio.IoUtils;

/**
 * @author Stuart Douglas
 */
public class URLResource implements Resource {

    private final URL url;
    private final URLConnection connection;
    private final String path;

    public URLResource(final URL url, final URLConnection connection, String path) {
        this.url = url;
        this.connection = connection;
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Date getLastModified() {
        return new Date(connection.getLastModified());
    }

    @Override
    public String getLastModifiedString() {
        return DateUtils.toDateString(getLastModified());
    }

    @Override
    public ETag getETag() {
        return null;
    }

    @Override
    public String getName() {
        String path = url.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int sepIndex = path.lastIndexOf("/");
        if (sepIndex != -1) {
            path = path.substring(sepIndex + 1);
        }
        return path;
    }

    @Override
    public boolean isDirectory() {
        File file = getFile();
        if(file != null) {
            return file.isDirectory();
        } else if(url.getPath().endsWith("/")) {
            return true;
        }
        return false;
    }

    @Override
    public List<Resource> list() {
        List<Resource> result = new LinkedList<>();
        File file = getFile();
        try {
            if (file != null) {
                for (File f : file.listFiles()) {
                    result.add(new URLResource(f.toURI().toURL(), connection, f.getPath()));
                }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        final String fileName = getName();
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index != fileName.length() - 1) {
            return mimeMappings.getMimeType(fileName.substring(index + 1));
        }
        return null;
    }

    @Override
    public void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback completionCallback) {

        class ServerTask implements Runnable, IoCallback {

            private InputStream inputStream;
            private byte[] buffer;

            @Override
            public void run() {
                if (inputStream == null) {
                    try {
                        inputStream = url.openStream();
                    } catch (IOException e) {
                        exchange.setResponseCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        return;
                    }
                    buffer = new byte[1024];//TODO: we should be pooling these
                }
                try {
                    int res = inputStream.read(buffer);
                    if (res == -1) {
                        //we are done, just return
                        IoUtils.safeClose(inputStream);
                        completionCallback.onComplete(exchange, sender);
                        return;
                    }
                    sender.send(ByteBuffer.wrap(buffer, 0, res), this);
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
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                IoUtils.safeClose(inputStream);
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(StatusCodes.INTERNAL_SERVER_ERROR);
                }
                completionCallback.onException(exchange, sender, exception);
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
        return (long) connection.getContentLength();
    }

    @Override
    public String getCacheKey() {
        return url.toString();
    }

    @Override
    public File getFile() {
        if(url.getProtocol().equals("file")) {
            try {
                return new File(url.toURI());
            } catch (URISyntaxException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public File getResourceManagerRoot() {
        return null;
    }

    @Override
    public URL getUrl() {
        return url;
    }
}
