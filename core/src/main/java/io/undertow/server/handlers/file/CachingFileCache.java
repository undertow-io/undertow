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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.WorkerDispatcher;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 * A file cache that caches
 *
 * @author Jason T. Greene
 */
public class CachingFileCache implements FileCache {

    private static final int DEFAULT_MAX_CACHE_FILE_SIZE = 2048 * 1024;

    private static final Logger log = Logger.getLogger("io.undertow.server.handlers.file");
    private static final String JDK7_NO_SUCH_FILE = "java.nio.file.NoSuchFileException";

    private final DirectBufferCache cache;
    private final long maxFileSize;

    public CachingFileCache(final int sliceSize, final int maxSlices, final long maxFileSize) {
        this.maxFileSize = maxFileSize;
        this.cache =  new DirectBufferCache(sliceSize, sliceSize * maxSlices);
    }

    public CachingFileCache(final int sliceSize, final int maxSlices) {
        this(sliceSize, maxSlices, DEFAULT_MAX_CACHE_FILE_SIZE);
    }

    @Override
    public void serveFile(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File file) {
        // ignore request body
        IoUtils.safeShutdownReads(exchange.getRequestChannel());
        final HttpString method = exchange.getRequestMethod();

        if (! (method.equals(Methods.GET) || method.equals(Methods.HEAD))) {
            exchange.setResponseCode(500);
            completionHandler.handleComplete();
            return;
        }
        final ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
        if (sendRequestedBlobs(exchange, completionHandler, factory)) {
            return;
        }

        final DirectBufferCache.CacheEntry entry = cache.get(file.getAbsolutePath());
        if (entry == null) {
            WorkerDispatcher.dispatch(exchange, new FileWriteLoadTask(exchange, completionHandler, factory, file));
            return;
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(entry.size()));
        if (method.equals(Methods.HEAD)) {
            completionHandler.handleComplete();
            return;
        }

        // It's loading retry later
        if (!entry.enabled() || !entry.reference()) {
            WorkerDispatcher.dispatch(exchange, new FileWriteLoadTask(exchange, completionHandler, factory, file));
            return;
        }

        final StreamSinkChannel responseChannel;
        final ByteBuffer[] buffers;


        boolean ok = false;
        try {
            responseChannel = factory.create();
            LimitedBufferSlicePool.PooledByteBuffer[] pooled = entry.buffers();
            buffers = new ByteBuffer[pooled.length];
            for (int i = 0; i < buffers.length; i++) {
                // Keep position from mutating
                buffers[i] = pooled[i].getResource().duplicate();
            }
            ok = true;
        } finally {
            if (!ok) {
                entry.dereference();
            }
        }

        // Transfer Inline, or register and continue transfer
        // Pass off the entry dereference call to the listener
        new TransferListener(entry, exchange, completionHandler, buffers, true).handleEvent(responseChannel);
    }

    private boolean sendRequestedBlobs(HttpServerExchange exchange, HttpCompletionHandler completionHandler, ChannelFactory<StreamSinkChannel> factory) {
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

            StreamSinkChannel channel = factory.create();
            new TransferListener(null, exchange, completionHandler, new ByteBuffer[] { buffer } , true).handleEvent(channel);
            return true;
        }

        return false;
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


    private class FileWriteLoadTask implements Runnable {

        private final HttpCompletionHandler completionHandler;
        private final File file;
        private final HttpServerExchange exchange;
        private final ChannelFactory<StreamSinkChannel> factory;

        public FileWriteLoadTask(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final ChannelFactory<StreamSinkChannel> factory, final File file) {
            this.completionHandler = completionHandler;
            this.factory = factory;
            this.file = file;
            this.exchange = exchange;
        }

        @Override
        public void run() {
            final HttpString method = exchange.getRequestMethod();
            final FileChannel fileChannel;
            final long length;

            if (file.isDirectory()) {
                renderDirectoryListing();
                return;
            }

            try {
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
                length = fileChannel.size();
            } catch (IOException e) {
                if (e instanceof FileNotFoundException || JDK7_NO_SUCH_FILE.equals(e.getClass().getName())) {
                    exchange.setResponseCode(404);
                } else {
                    exchange.setResponseCode(500);
                }
                completionHandler.handleComplete();
                return;
            }


            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(length));
            if (method.equals(Methods.HEAD)) {
                completionHandler.handleComplete();
                return;
            }
            if (!method.equals(Methods.GET)) {
                exchange.setResponseCode(500);
                completionHandler.handleComplete();
                return;
            }

            final StreamSinkChannel channel = factory.create();

            DirectBufferCache.CacheEntry entry = null;
            String path = file.getAbsolutePath();
            if (length < maxFileSize) {
                entry = cache.add(path, (int) length);
            }

            if (entry == null || entry.buffers().length == 0 || !entry.claimEnable()) {
                transfer(channel, fileChannel, length);
                return;
            }

            if (! entry.reference()) {
                entry.disable();
                transfer(channel, fileChannel, length);
                return;
            }

            ByteBuffer[] buffers;
            boolean ok = false;
            try {
                buffers =  populateBuffers(fileChannel, length, entry);
                if (buffers == null ) {
                    // File I/O exception, cleanup required
                    return;
                }
                entry.enable();
                ok = true;
            } finally {
                IoUtils.safeClose(fileChannel);
                if (!ok) {
                    entry.dereference();
                    entry.disable();
                }
            }

            // Now that the cache is loaded, attempt to write or register a lister
            // Also, pass off entry dereference to the listener
            new TransferListener(entry, exchange, completionHandler, buffers, true).handleEvent(channel);
        }

        private void renderDirectoryListing() {
            String requestPath = exchange.getRequestPath();
            if (! requestPath.endsWith("/")) {
                exchange.setResponseCode(302);
                exchange.getResponseHeaders().put(Headers.LOCATION, requestPath + "/");
                completionHandler.handleComplete();
                return;
            }

            // TODO - Fix exchange to sanitize path
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


        private ByteBuffer[] populateBuffers(FileChannel fileChannel, long length, DirectBufferCache.CacheEntry entry) {
            LimitedBufferSlicePool.PooledByteBuffer[] pooled = entry.buffers();
            ByteBuffer[] buffers = new ByteBuffer[pooled.length];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = pooled[i].getResource();
            }

            long remaining = length;
            while (remaining > 0) {
                try {
                    long res = fileChannel.read(buffers);
                    if (res > 0) {
                        remaining -= res;
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(fileChannel);
                    exchange.setResponseCode(500);
                    completionHandler.handleComplete();
                    return null;
                }
            }

            ByteBuffer lastBuffer = buffers[buffers.length - 1];
            lastBuffer.limit(lastBuffer.position());

            for (int i = 0; i < buffers.length; i++) {
                // Prepare for reading
                buffers[i].position(0);

                // Prevent mutation when writing below
                buffers[i] = buffers[i].duplicate();
            }

            return buffers;
        }


        private void transfer(StreamSinkChannel channel, FileChannel fileChannel, long length) {
            try {
                log.tracef("Serving file %s (blocking)", fileChannel);
                Channels.transferBlocking(channel, fileChannel, 0, length);
                log.tracef("Finished serving %s, shutting down (blocking)", fileChannel);
                channel.shutdownWrites();
                log.tracef("Finished serving %s, flushing (blocking)", fileChannel);
                Channels.flushBlocking(channel);
                log.tracef("Finished serving %s (complete)", fileChannel);
            } catch (IOException ignored) {
                log.tracef("Failed to serve %s: %s", fileChannel, ignored);
            } finally {
                IoUtils.safeClose(fileChannel);
                completionHandler.handleComplete();
            }
        }
    }

    private static class TransferListener implements ChannelListener<StreamSinkChannel> {
        private final HttpCompletionHandler completionHandler;
        private final ByteBuffer[] buffers;
        private final boolean recurse;
        private final DirectBufferCache.CacheEntry entry;
        private final HttpServerExchange exchange;

        public TransferListener(DirectBufferCache.CacheEntry entry, HttpServerExchange exchange, HttpCompletionHandler completionHandler, ByteBuffer[] buffers, boolean recurse) {
            this.entry = entry;
            this.completionHandler = completionHandler;
            this.buffers = buffers;
            this.recurse = recurse;
            this.exchange = exchange;
        }

        public void handleEvent(final StreamSinkChannel channel) {
            boolean dereference = true;
            try {
                ByteBuffer last = buffers[buffers.length - 1];
                while (last.remaining() > 0) {
                    long res;
                    try {
                        res = channel.write(buffers);
                    } catch (IOException e) {
                        IoUtils.safeClose(channel);
                        completionHandler.handleComplete();
                        exchange.setResponseCode(500);
                        return;
                    }

                    if (res == 0L) {
                        if (recurse) {
                            channel.getWriteSetter().set(new TransferListener(entry, exchange, completionHandler, buffers, false));
                            channel.resumeWrites();
                        }
                        dereference = false; // Entry still in-use
                        return;
                    }
                }
            } finally {
                if (dereference && entry != null) {
                    entry.dereference();
                }
            }
            HttpHandlers.flushAndCompleteRequest(channel, completionHandler);
        }
    }
}
