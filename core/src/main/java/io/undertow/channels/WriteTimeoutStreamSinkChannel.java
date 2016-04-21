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
import org.xnio.Buffers;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Wrapper for write timeout. This should always be the first wrapper applied to the underlying channel.
 * <p>
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#WRITE_TIMEOUT
 */
@Deprecated
public final class WriteTimeoutStreamSinkChannel extends DelegatingStreamSinkChannel<WriteTimeoutStreamSinkChannel> {

    private int writeTimeout;
    private XnioExecutor.Key handle;

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity");
            try {
                if (delegate.isWriteResumed()) {
                    ChannelListeners.invokeChannelListener(WriteTimeoutStreamSinkChannel.this, writeSetter.get());
                }
            } finally {
                IoUtils.safeClose(delegate);
            }
        }
    };

    /**
     * @param delegate    The underlying channel
     */
    public WriteTimeoutStreamSinkChannel(final StreamSinkChannel delegate) {
        super(delegate);
        try {
            Integer timeout = delegate.getOption(Options.WRITE_TIMEOUT);
            if (timeout != null) {
                this.writeTimeout = timeout;
            } else {
                this.writeTimeout = 0;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleWriteTimeout(final long ret) {
        if (writeTimeout > 0) {
            if (ret == 0 && handle == null) {
                handle = delegate.getWriteThread().executeAfter(timeoutCommand, writeTimeout, TimeUnit.MILLISECONDS);
            } else if (ret > 0 && handle != null) {
                handle.remove();
            }
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        int ret = delegate.write(src);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long ret = delegate.write(srcs, offset, length);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int ret = delegate.writeFinal(src);
        handleWriteTimeout(ret);
        if(!src.hasRemaining()) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        return ret;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long ret = delegate.writeFinal(srcs, offset, length);
        handleWriteTimeout(ret);
        if(!Buffers.hasRemaining(srcs, offset, length)) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        return ret;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        long ret = delegate.writeFinal(srcs);
        handleWriteTimeout(ret);
        if(!Buffers.hasRemaining(srcs)) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        return ret;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long ret = delegate.transferFrom(src, position, count);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        long ret = delegate.transferFrom(source, count, throughBuffer);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T ret = super.setOption(option, value);
        if (option == Options.WRITE_TIMEOUT) {
            writeTimeout = (Integer) value;
            if (handle != null) {
                handle.remove();
                if(writeTimeout > 0) {
                    getWriteThread().executeAfter(timeoutCommand, writeTimeout, TimeUnit.MILLISECONDS);
                }
            }
        }
        return ret;
    }

    @Override
    public void shutdownWrites() throws IOException {
        super.shutdownWrites();
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
