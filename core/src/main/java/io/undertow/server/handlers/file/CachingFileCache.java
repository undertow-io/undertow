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
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
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
        final String method = exchange.getRequestMethod();

        if (! method.equalsIgnoreCase(Methods.GET)) {
            exchange.setResponseCode(500);
            completionHandler.handleComplete();
            return;
        }
        final ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
        final DirectBufferCache.CacheEntry entry = cache.get(file.getAbsolutePath());
        if (entry == null) {
            exchange.getConnection().getWorker().execute(new FileWriteLoadTask(exchange, completionHandler, factory, file));
            return;
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(entry.size()));
        if (method.equalsIgnoreCase(Methods.HEAD)) {
            completionHandler.handleComplete();
            return;
        }

        // It's loading retry later
        if (!entry.enabled() || !entry.reference()) {
            exchange.getConnection().getWorker().execute(new FileWriteLoadTask(exchange, completionHandler, factory, file));
            return;
        }

        final StreamSinkChannel responseChannel;
        final ByteBuffer[] buffers;

        try {
            responseChannel = factory.create();
            LimitedBufferSlicePool.PooledByteBuffer[] pooled = entry.buffers();
            buffers = new ByteBuffer[pooled.length];
            for (int i = 0; i < buffers.length; i++) {
                // Keep position from mutating
                buffers[i] = pooled[i].getResource().duplicate();
            }
        } catch (Throwable t)  {
            entry.dereference();
            safeSetResponse(exchange, 500);
            safeComplete(completionHandler);

            if (t instanceof Error) {
                throw (Error) t;
            }
            return;
        }

        // Transfer Inline, or register and continue transfer
        new TransferListener(entry, exchange, completionHandler, buffers, true).handleEvent(responseChannel);
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
            final String method = exchange.getRequestMethod();
            final FileChannel fileChannel;
            final long length;
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
            if (method.equalsIgnoreCase(Methods.HEAD)) {
                completionHandler.handleComplete();
                return;
            }
            if (!method.equalsIgnoreCase(Methods.GET)) {
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
            try {
                buffers =  populateBuffers(fileChannel, length, entry);
                if (buffers == null ) {
                    return;
                }
                entry.enable();
            } catch (Throwable t) {
                entry.dereference();
                entry.disable();
                exchange.setResponseCode(500);
                completionHandler.handleComplete();

                if (t instanceof Error) {
                    throw (Error) t;
                }

                log.debug("Exception thrown during buffer population", t);
                return;
            } finally {
                IoUtils.safeClose(fileChannel);
            }

            // Now that the cache is loaded, attempt to write or register a lister
            new TransferListener(entry, exchange, completionHandler, buffers, true).handleEvent(channel);
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
                    entry.disable();
                    entry.dereference();
                    safeSetResponse(exchange, 500);
                    safeComplete(completionHandler);
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

    private static void safeSetResponse(HttpServerExchange exchange, int status) {
        try {
            exchange.setResponseCode(status);
        } catch (Throwable t) {
        }
    }

      private static void safeComplete(HttpCompletionHandler handler) {
        try {
            handler.handleComplete();
        } catch (Throwable t) {
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
                            channel.getWriteSetter().set(new TransferListener(entry, exchange,completionHandler, buffers, false));
                            channel.resumeWrites();
                        }
                        return;
                    }
                }
            } catch (Throwable t) {
                IoUtils.safeClose(channel);
                entry.dereference();
                safeSetResponse(exchange, 500);
                safeComplete(completionHandler);
                if (t instanceof Error) {
                    throw (Error) t;
                }

                log.debug("Exception thrown during buffer write", t);
                return;
            }
            entry.dereference();
            HttpHandlers.flushAndCompleteRequest(channel, completionHandler);
        }
    }
}
