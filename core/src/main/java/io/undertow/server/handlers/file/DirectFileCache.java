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
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
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

        WorkerDispatcher.dispatch(exchange, new FileWriteTask(exchange, completionHandler, file));
    }

    private static class FileWriteTask implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final File file;

        private FileWriteTask(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File file) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.file = file;
        }

        @Override
        public void run() {

            final HttpString method = exchange.getRequestMethod();
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
            if (method.equals(Methods.HEAD)) {
                completionHandler.handleComplete();
                return;
            }
            if (!method.equals(Methods.GET)) {
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


            try {
                log.tracef("Serving file %s (blocking)", fileChannel);
                Channels.transferBlocking(response, fileChannel, 0, length);
                log.tracef("Finished serving %s, shutting down (blocking)", fileChannel);
                response.shutdownWrites();
                log.tracef("Finished serving %s, flushing (blocking)", fileChannel);
                Channels.flushBlocking(response);
                log.tracef("Finished serving %s (complete)", fileChannel);
                completionHandler.handleComplete();
            } catch (IOException ignored) {
                log.tracef("Failed to serve %s: %s", fileChannel, ignored);
                IoUtils.safeClose(fileChannel);
                completionHandler.handleComplete();
            } finally {
                IoUtils.safeClose(fileChannel);
                IoUtils.safeClose(response);
            }
        }
    }
}
