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
import java.nio.channels.FileChannel;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

/**
 * A file cache that serves files directly with no caching.
 *
 * @author Stuart Douglas
 */
public class DirectFileCache implements FileCache {

    public static final FileCache INSTANCE = new DirectFileCache();

    @Override
    public void serveFile(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File file) {
        try {
            final FileChannel fileChannel;
            try {
                fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
            } catch (FileNotFoundException e) {
                exchange.setResponseCode(404);
                completionHandler.handleComplete();
                return;
            }
            final long length = fileChannel.size();
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + length);
            final StreamSinkChannel response = exchange.getResponseChannelFactory().create();
            assert response == null;
            final FileWriteTask task = new FileWriteTask(completionHandler, response, fileChannel, file, length);
            response.getWorker().submit(task);
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(e, file);
            exchange.setResponseCode(500);
            completionHandler.handleComplete();
            return;
        }
    }

    private static class FileWriteTask implements Runnable, ChannelListener<StreamSinkChannel> {

        private final HttpCompletionHandler completionHandler;
        private final StreamSinkChannel channel;
        private final FileChannel fileChannel;
        private final File file;
        private final long length;
        private long written;

        public FileWriteTask(final HttpCompletionHandler completionHandler, final StreamSinkChannel channel, final FileChannel fileChannel, final File file, final long length) {
            this.completionHandler = completionHandler;
            this.channel = channel;
            this.fileChannel = fileChannel;
            this.file = file;
            this.length = length;
        }

        @Override
        public synchronized void run() {
            try {
                long c;
                do {
                    c = channel.transferFrom(fileChannel, written, length - written);
                    written += c;
                } while (written < length && c > 0);
                if (written < length) {
                    channel.getWriteSetter().set(this);
                    channel.resumeWrites();
                } else {
                    channel.getWriteSetter().set(null);
                    IoUtils.safeClose(fileChannel);
                    completionHandler.handleComplete();
                }
            } catch (IOException e) {
                IoUtils.safeClose(fileChannel);
                UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(e, file);
                completionHandler.handleComplete();
            }
        }

        @Override
        public void handleEvent(final StreamSinkChannel channel) {
            channel.getWorker().submit(this);
            channel.suspendWrites();
        }
    }
}
