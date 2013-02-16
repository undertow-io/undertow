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
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.util.HttpString;
import io.undertow.util.WorkerDispatcher;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;

/**
 * A file cache that serves files directly with no caching.
 *
 * @author Stuart Douglas
 */
public class DirectFileSource implements FileSource {

    private static final Logger log = Logger.getLogger("io.undertow.server.handlers.file");

    public static final FileSource INSTANCE = new DirectFileSource();

    @Override
    public void serveFile(final HttpServerExchange exchange, final File file, final boolean directoryListingEnabled) {
        // ignore request body

        //try and serve a cached version, and also mark the response as cachable
        final ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
        if(cache != null) {
            if(cache.tryServeResponse()) {
                return;
            }
        }
        WorkerDispatcher.dispatch(exchange, new FileWriteTask(exchange, file, directoryListingEnabled));
    }

    private static class FileWriteTask implements Runnable {

        private final HttpServerExchange exchange;
        private final File file;
        private final boolean directoryListingEnabled;

        private FileWriteTask(final HttpServerExchange exchange, final File file, final boolean directoryListingEnabled) {
            this.exchange = exchange;
            this.file = file;
            this.directoryListingEnabled = directoryListingEnabled;
        }

        @Override
        public void run() {

            if(file.isDirectory()) {
                if (directoryListingEnabled) {
                    FileHandler.renderDirectoryListing(exchange, file);
                } else {
                    exchange.setResponseCode(404);
                    exchange.endExchange();
                }
                return;
            }

            final HttpString method = exchange.getRequestMethod();
            final FileChannel fileChannel;
            final long length;
            try {
                try {
                    fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
                } catch (FileNotFoundException e) {
                    exchange.setResponseCode(404);
                    exchange.endExchange();
                    return;
                }
                length = fileChannel.size();
            } catch (IOException e) {
                UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(file, e);
                exchange.setResponseCode(500);
                exchange.endExchange();
                return;
            }
            if (!method.equals(GET) && !method.equals(HEAD)) {
                exchange.setResponseCode(500);
                exchange.endExchange();
                return;
            }

            exchange.getResponseHeaders().put(CONTENT_LENGTH, Long.toString(length));
            if (method.equals(HEAD)) {
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
                Channels.transferBlocking(response, fileChannel, 0, length);
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
    }
}
