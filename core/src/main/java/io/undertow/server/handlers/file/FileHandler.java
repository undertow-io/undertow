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

package io.undertow.server.handlers.file;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 *
 * Serves files direct from the file system.
 *
 * @author Stuart Douglas
 * @author Jason T. Greene
 */
public class FileHandler implements HttpHandler {

    private volatile File base;
    private volatile FileCache fileCache = new CachingFileCache(1024, 10480);

    public FileHandler(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.base = base;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        String path = exchange.getRelativePath();
        if (File.separatorChar != '/') {
            if (path.indexOf(File.separatorChar) != -1) {
                exchange.setResponseCode(404);
                completionHandler.handleComplete();
                return;
            }
            path = path.replace('/', File.separatorChar);
        }

        if (sendRequestedBlobs(exchange, completionHandler)) {
            return;
        }

        fileCache.serveFile(exchange, completionHandler, new File(base, path));
    }

    public File getBase() {
        return base;
    }

    public void setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.base = base;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(final FileCache fileCache) {
        if (fileCache == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.fileCache = fileCache;
    }

    private boolean sendRequestedBlobs(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        ByteBuffer buffer = null;
        String type = null;
        if ("css".equals(exchange.getQueryString())) {
            buffer = Blobs.FILE_CSS_BUFFER.duplicate();
            type = "text/css";
        } else if ("js".equals(exchange.getQueryString())) {
            buffer = Blobs.FILE_JS_BUFFER.duplicate();
            type = "application/javascript";
        }

        if (buffer != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(buffer.limit()));
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, type);
            if (Methods.HEAD.equals(exchange.getRequestMethod())) {
                completionHandler.handleComplete();
                return true;
            }

            ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
            StreamSinkChannel channel = factory.create();
            BufferTransfer.transfer(exchange, channel, completionHandler, null, new ByteBuffer[]{buffer});

            return true;
        }

        return false;
    }

    public static void renderDirectoryListing(HttpServerExchange exchange, HttpCompletionHandler completionHandler, File file, ChannelFactory<StreamSinkChannel> factory) {
        String requestPath = exchange.getRequestPath();
        if (! requestPath.endsWith("/")) {
            exchange.setResponseCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, requestPath + "/");
            completionHandler.handleComplete();
            return;
        }

        // TODO - Fix exchange to sanitize path, so handlers don't need to do this
        String resolvedPath = exchange.getResolvedPath();
        for (int i = 0; i < resolvedPath.length(); i++) {
            if (resolvedPath.charAt(i) != '/') {
                resolvedPath = resolvedPath.substring(Math.max(0, i - 1));
                break;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<html><head><script src='").append(resolvedPath).append("?js'></script>")
                .append("<link rel='stylesheet' type='txt/css' href='").append(resolvedPath).append("?css'/></head>");
        builder.append("<body onresize='growit()' onload='growit()'><table id='thetable'><thead>");
        builder.append("<tr><th class='loc' colspan='3'>Directory Listing - ").append(requestPath)
                .append("<tr><th class='label offset'>Name</th><th class='label'>Last Modified</th><th class='label'>Size</th></tr></thead>")
                .append("<tfoot><tr><th class=\"loc footer\" colspan=\"3\">Powered by Undertow</th></tr></tfoot><tbody>");

        int state  = 0;
        String parent = null;
        for (int i = requestPath.length() - 1; i >= 0; i--) {
            if (state == 1) {
                if (requestPath.charAt(i) == '/') {
                    state = 2;
                }
            } else if (requestPath.charAt(i) != '/') {
                if (state == 2) {
                    parent = requestPath.substring(0, i + 1);
                    break;
                }
                state = 1;
            }
        }

        SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");
        int i = 0;
        if (parent != null) {
            i++;
            builder.append("<tr class='odd'><td><a class='icon up' href='").append(parent).append("'>[..]</a></td><td>");
            builder.append(format.format(new Date(file.lastModified()))).append("</td><td>--</td></tr>");
        }

        for (File entry : file.listFiles()) {
            builder.append("<tr class='").append((++i & 1) == 1 ? "odd" : "even").append("'><td><a class='icon ");
            builder.append(entry.isFile() ? "file" : "dir");
            builder.append("' href='").append(entry.getName()).append("'>").append(entry.getName()).append("</a></td><td>");
            builder.append(format.format(new Date(file.lastModified()))).append("</td><td>");
            if (entry.isFile()) {
                formatSize(builder, entry.length());
            } else {
                builder.append("--");
            }
            builder.append("</td></tr>");
        }
        builder.append("</tbody></table></body></html>");

        try {
            ByteBuffer output = ByteBuffer.wrap(builder.toString().getBytes("UTF-8"));
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(output.limit()));
            Channels.writeBlocking(factory.create(), output);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            exchange.setResponseCode(500);
        }

        completionHandler.handleComplete();
        return;
    }


    private static StringBuilder formatSize(StringBuilder builder, long size) {
        int n = 1024 * 1024 * 1024;
        int type = 0;
        while (size < n && n >= 1024) {
            n /= 1024;
            type++;
        }

        long top = (size * 100) / n;
        long bottom =  top % 100;
        top /= 100;

        builder.append(top);
        if (bottom > 0) {
            builder.append(".").append(bottom / 10);
            bottom %= 10;
            if (bottom > 0) {
                builder.append(bottom);
            }

        }

        switch (type) {
            case 0: builder.append(" GB"); break;
            case 1: builder.append(" MB"); break;
            case 2: builder.append(" KB"); break;
        }

        return builder;
    }
}