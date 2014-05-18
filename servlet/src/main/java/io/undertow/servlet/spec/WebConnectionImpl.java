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
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.WebConnection;

import org.xnio.Pool;
import org.xnio.StreamConnection;

/**
 * @author Stuart Douglas
 */
public class WebConnectionImpl implements WebConnection {

    private final UpgradeServletOutputStream outputStream;
    private final UpgradeServletInputStream inputStream;
    private final Executor ioExecutor;

    public WebConnectionImpl(final StreamConnection channel, Pool<ByteBuffer> bufferPool, Executor ioExecutor) {
        this.ioExecutor = ioExecutor;
        this.outputStream = new UpgradeServletOutputStream(channel.getSinkChannel(), ioExecutor);
        this.inputStream = new UpgradeServletInputStream(channel.getSourceChannel(), bufferPool, ioExecutor);
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
        outputStream.closeBlocking();
    }
}
