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
import io.undertow.util.CompletionChannelExceptionHandler;
import io.undertow.util.CompletionChannelListener;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.SuspendableWriteChannel;

/**
 * A file cache that serves files directly with no caching using non-blocking I/O (may block on file access).
 *
 * @author Stuart Douglas
 */
public class InLineFileCache implements FileCache {

    public static final FileCache INSTANCE = new InLineFileCache();

    @Override
    public void serveFile(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File file) {
        // ignore request body
        IoUtils.safeShutdownReads(exchange.getRequestChannel());
        final String method = exchange.getRequestMethod();
        final FileChannel fileChannel;
        long length;
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
        final StreamSinkChannel responseChannel = factory.create();
        responseChannel.getCloseSetter().set(new ChannelListener<Channel>() {
            public void handleEvent(final Channel channel) {
                IoUtils.safeClose(fileChannel);
                completionHandler.handleComplete();
            }
        });
        long pos = 0L;
        long res;
        while (length > 0L) {
            try {
                res = responseChannel.transferFrom(fileChannel, pos, length);
            } catch (IOException e) {
                IoUtils.safeClose(fileChannel);
                IoUtils.safeClose(responseChannel);
                completionHandler.handleComplete();
                return;
            }
            if (res == 0L) {
                responseChannel.getWriteSetter().set(new TransferListener(length, pos, responseChannel, fileChannel, completionHandler));
                responseChannel.resumeWrites();
                return;
            }
            pos += res;
            length -= res;
        }
        IoUtils.safeClose(fileChannel);
        try {
            responseChannel.shutdownWrites();
            if (! responseChannel.flush()) {
                responseChannel.getWriteSetter().set(ChannelListeners.<SuspendableWriteChannel>flushingChannelListener(new CompletionChannelListener(completionHandler), new CompletionChannelExceptionHandler(completionHandler)));
                responseChannel.resumeWrites();
                return;
            }
        } catch (IOException e) {
            IoUtils.safeClose(fileChannel);
            IoUtils.safeClose(responseChannel);
            completionHandler.handleComplete();
            return;
        }
    }

    private static class TransferListener implements ChannelListener<StreamSinkChannel> {

        private long length;
        private long pos;
        private final StreamSinkChannel responseChannel;
        private final FileChannel fileChannel;
        private final HttpCompletionHandler completionHandler;

        public TransferListener(final long length, final long pos, final StreamSinkChannel responseChannel, final FileChannel fileChannel, final HttpCompletionHandler completionHandler) {
            this.length = length;
            this.pos = pos;
            this.responseChannel = responseChannel;
            this.fileChannel = fileChannel;
            this.completionHandler = completionHandler;
        }

        public void handleEvent(final StreamSinkChannel channel) {
            long res;
            long length = this.length;
            long pos = this.pos;
            try {
                while (length > 0L) {
                    try {
                        res = responseChannel.transferFrom(fileChannel, pos, length);
                    } catch (IOException e) {
                        IoUtils.safeClose(fileChannel);
                        responseChannel.suspendWrites();
                        IoUtils.safeClose(responseChannel);
                        completionHandler.handleComplete();
                        return;
                    }
                    if (res == 0L) {
                        return;
                    }
                    pos += res;
                    length -= res;
                }
                IoUtils.safeClose(fileChannel);
                try {
                    responseChannel.shutdownWrites();
                    if (! responseChannel.flush()) {
                        responseChannel.getWriteSetter().set(ChannelListeners.<SuspendableWriteChannel>flushingChannelListener(new CompletionChannelListener(completionHandler), new CompletionChannelExceptionHandler(completionHandler)));
                        responseChannel.resumeWrites();
                        return;
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(fileChannel);
                    responseChannel.suspendWrites();
                    IoUtils.safeClose(responseChannel);
                    completionHandler.handleComplete();
                    return;
                }
            } finally {
                this.length = length;
                this.pos = pos;
            }
        }
    }
}
