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

package io.undertow.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.Xnio;
import org.xnio.channels.StreamSinkChannel;

/**
 * A simple write listener that can be used to write out the contents of a file. When the file is written
 * out it closes the channel.
 * <p/>
 * This should not be added directly to the channel, instead {@link #setup(io.undertow.server.HttpServerExchange, org.xnio.channels.StreamSinkChannel)}
 * should be called, which will attempt a write, and only add the listener if required.
 *
 * @author Stuart Douglas
 */
public class FileWriteChannelListener implements ChannelListener<StreamSinkChannel> {

    private final File file;
    private final FileChannel fileChannel;
    private final ExecutorService executorService;
    private final long length;
    private FileWriteTask writeTask;
    private int written;


    public FileWriteChannelListener(final File file, final Xnio xnio, final ExecutorService executorService) throws IOException {
        this.file = file;
        this.fileChannel = xnio.openFile(file, FileAccess.READ_ONLY);
        this.length = file.length();
        this.executorService = executorService;
    }

    public void setup(final HttpServerExchange exchange, final StreamSinkChannel channel) {
        this.writeTask = new FileWriteTask(channel);
        exchange.getResponseHeaders().add(Headers.CONTENT_LENGTH, "" + length);
        executorService.submit(writeTask);
    }

    @Override
    public void handleEvent(final StreamSinkChannel channel) {
        executorService.submit(writeTask);
    }

    protected void done(final StreamSinkChannel channel, final Exception exception) {
        IoUtils.safeClose(channel);
    }

    private class FileWriteTask implements Runnable {
        private final StreamSinkChannel channel;

        public FileWriteTask(final StreamSinkChannel channel) {
            this.channel = channel;
        }

        @Override
        public synchronized void run() {
            if (!channel.isOpen()) {
                return;
            }
            try {
                long c;
                do {
                    c = channel.transferFrom(fileChannel, written, length);
                    written += c;
                } while (written < length && c > 0);
                if (written < length) {
                    channel.getWriteSetter().set(FileWriteChannelListener.this);
                    channel.resumeWrites();
                } else {
                    IoUtils.safeClose(fileChannel);
                    done(channel, null);
                }
            } catch (IOException e) {
                IoUtils.safeClose(fileChannel);
                UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(e, file);
                done(channel, e);
            }
        }
    }
}
