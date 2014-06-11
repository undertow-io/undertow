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
package io.undertow.websockets.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class StreamSinkChannelAdapter implements StreamSinkChannel {

    private final ChannelListener.SimpleSetter<? extends StreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<>();
    private final ChannelListener.SimpleSetter<? extends StreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<>();

    private final WritableByteChannel channel;

    public StreamSinkChannelAdapter(WritableByteChannel channel) {
        this.channel = channel;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long written = 0;
        for (int i = offset; i < length; i++) {
            int w = write(srcs[i]);
            if (w < 0) {
                return w;
            }
            written += w;
        }
        return written;
    }

    @Override
    public void suspendWrites() {
        // Noop
    }

    @Override
    public void resumeWrites() {
        // Noop
    }

    @Override
    public boolean isWriteResumed() {
        return false;
    }

    @Override
    public void wakeupWrites() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownWrites() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void awaitWritable() throws IOException {

    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {

    }

    @Override
    public XnioExecutor getWriteThread() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean flush() throws IOException {
        return true;
    }

    @Override
    public XnioWorker getWorker() {
        throw new UnsupportedOperationException();
    }

    @Override
    public XnioIoThread getIoThread() {
        return null;
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IOException {
        return null;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return src.transferTo(position, count, channel);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        long transferred = 0;
        while (transferred < count) {
            int r = source.read(throughBuffer);
            if (r > 0) {
                throughBuffer.flip();
                while(throughBuffer.hasRemaining()) {
                    int w = write(throughBuffer);
                    if (w < 1) {
                        throughBuffer.flip();
                        return transferred;
                    } else {
                        transferred += w;
                    }
                }
                throughBuffer.clear();
            }
            return transferred;
        }
        return transferred;
    }

    @Override
    public Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Channels.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Channels.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return Channels.writeFinalBasic(this, srcs, 0, srcs.length);
    }


}
