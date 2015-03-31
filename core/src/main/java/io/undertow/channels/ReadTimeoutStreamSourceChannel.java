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

package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Wrapper for read timeout. This should always be the first wrapper applied to the underlying channel.
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#READ_TIMEOUT
 */
public final class ReadTimeoutStreamSourceChannel extends DelegatingStreamSourceChannel<ReadTimeoutStreamSourceChannel> {

    private int readTimeout;
    private XnioExecutor.Key handle;

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity");
            try {
                if (delegate.isReadResumed()) {
                    ChannelListeners.invokeChannelListener(ReadTimeoutStreamSourceChannel.this, readSetter.get());
                }
            } finally {
                IoUtils.safeClose(delegate);
            }
        }
    };

    /**
     * @param delegate    The underlying channel
     */
    public ReadTimeoutStreamSourceChannel(final StreamSourceChannel delegate) {
        super(delegate);
        try {
            Integer timeout = delegate.getOption(Options.READ_TIMEOUT);
            if (timeout != null) {
                this.readTimeout = timeout;
            } else {
                this.readTimeout = 0;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleReadTimeout(final long ret) {
        if(ret == -1) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        } else if (readTimeout > 0) {
            if (ret == 0 && handle == null) {
                handle = delegate.getIoThread().executeAfter(timeoutCommand, readTimeout, TimeUnit.MILLISECONDS);
            } else if (ret > 0 && handle != null) {
                handle.remove();
            }
        }
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long ret = delegate.transferTo(position, count, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        long ret = delegate.transferTo(count, throughBuffer, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long ret = delegate.read(dsts, offset, length);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long read(final ByteBuffer[] dsts) throws IOException {
        long ret = delegate.read(dsts);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        int ret = delegate.read(dst);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T ret = super.setOption(option, value);
        if (option == Options.READ_TIMEOUT) {
            readTimeout = (Integer) value;
            if (handle != null) {
                handle.remove();
                if (readTimeout > 0) {
                    getReadThread().executeAfter(timeoutCommand, readTimeout, TimeUnit.MILLISECONDS);
                }
            }
        }
        return ret;
    }

    @Override
    public void shutdownReads() throws IOException {
        super.shutdownReads();
        if(handle != null) {
            handle.remove();
            handle = null;
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        if(handle != null) {
            handle.remove();
            handle = null;
        }
    }
}
