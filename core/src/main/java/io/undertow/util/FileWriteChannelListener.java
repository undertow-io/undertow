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

import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.Xnio;
import org.xnio.channels.StreamSinkChannel;

/**
 * A simple write listener that can be used to write out the contents of a file. When the file is written
 * out it closes the channel.
 *
 *
 * @author Stuart Douglas
 */
public class FileWriteChannelListener implements ChannelListener<StreamSinkChannel> {

    private final FileChannel file;
    private final ExecutorService executorService;
    private final long length;
    private int written;


    public FileWriteChannelListener(final File file, final Xnio xnio, final ExecutorService executorService) throws IOException {
        this.file = xnio.openFile(file, FileAccess.READ_ONLY);
        this.length = file.length();
        this.executorService = executorService;
    }

    @Override
    public void handleEvent(final StreamSinkChannel channel) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    long c;
                    do {
                        c = channel.transferFrom(file, written, length);
                        written += c;
                    } while (written < length && c > 0);
                    if (written < length) {
                        channel.resumeWrites();
                    } else {
                        writeDone(channel);
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                }
            }
        });
    }

    protected void writeDone(final StreamSinkChannel channel) {
        IoUtils.safeClose(channel);
    }
}
