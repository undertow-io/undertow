/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.spec;

import java.io.IOException;
import java.util.concurrent.Executor;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.WebConnection;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;

/**
 * @author Stuart Douglas
 */
public class WebConnectionImpl implements WebConnection {

    private final StreamConnection channel;
    private final UpgradeServletOutputStream outputStream;
    private final UpgradeServletInputStream inputStream;
    private final Executor ioExecutor;

    public WebConnectionImpl(final StreamConnection channel, ByteBufferPool bufferPool, Executor ioExecutor) {
        this.channel = channel;
        this.ioExecutor = ioExecutor;
        this.outputStream = new UpgradeServletOutputStream(channel.getSinkChannel(), ioExecutor);
        this.inputStream = new UpgradeServletInputStream(channel.getSourceChannel(), bufferPool, ioExecutor);
        channel.getCloseSetter().set(new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection channel) {
                try {
                    close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public void close() throws Exception {
        try {
            outputStream.closeBlocking();
        } finally {
            IoUtils.safeClose(inputStream, channel);
        }
    }
}
