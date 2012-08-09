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

import io.undertow.util.Methods;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 * A file cache that serves files directly with no caching.
 *
 * @author Stuart Douglas
 */
public class DirectFileCache implements FileCache {

    private static final Logger log = Logger.getLogger("io.undertow.server.handlers.file");

    public static final FileCache INSTANCE = new DirectFileCache();

    @Override
    public void serveFile(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File file) {
        // ignore request body
        IoUtils.safeShutdownReads(exchange.getRequestChannel());
        final String method = exchange.getRequestMethod();
        final FileChannel fileChannel;
        final long length;
        try {
            try {
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
            } catch (FileNotFoundException e) {
                exchange.setResponseCode(404);
                completionHandler.handleComplete();
                return;
            }
            length = fileChannel.size();
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(file, e);
            exchange.setResponseCode(500);
            completionHandler.handleComplete();
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(length));
        if (method.equalsIgnoreCase(Methods.HEAD)) {
            completionHandler.handleComplete();
            return;
        }
        if (! method.equalsIgnoreCase(Methods.GET)) {
            exchange.setResponseCode(500);
            completionHandler.handleComplete();
            return;
        }
        final ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
        if (factory == null) {
            IoUtils.safeClose(fileChannel);
            completionHandler.handleComplete();
            return;
        }
        final StreamSinkChannel response = factory.create();
        response.getCloseSetter().set(new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(fileChannel);
            }
        });
        response.getWorker().execute(new FileWriteTask(completionHandler, response, fileChannel, length));
    }

    private static class FileWriteTask implements Runnable {

        private final HttpCompletionHandler completionHandler;
        private final StreamSinkChannel channel;
        private final FileChannel fileChannel;
        private final long length;

        public FileWriteTask(final HttpCompletionHandler completionHandler, final StreamSinkChannel channel, final FileChannel fileChannel, final long length) {
            this.completionHandler = completionHandler;
            this.channel = channel;
            this.fileChannel = fileChannel;
            this.length = length;
        }

        @Override
        public void run() {
            try {
                log.tracef("Serving file %s (blocking)", fileChannel);
                Channels.transferBlocking(channel, fileChannel, 0, length);
                log.tracef("Finished serving %s, shutting down (blocking)", fileChannel);
                channel.shutdownWrites();
                log.tracef("Finished serving %s, flushing (blocking)", fileChannel);
                Channels.flushBlocking(channel);
                log.tracef("Finished serving %s (complete)", fileChannel);
                completionHandler.handleComplete();
            } catch (IOException ignored) {
                log.tracef("Failed to serve %s: %s", fileChannel, ignored);
                IoUtils.safeClose(fileChannel);
                completionHandler.handleComplete();
            } finally {
                IoUtils.safeClose(fileChannel);
                IoUtils.safeClose(channel);
            }
        }
    }
}
