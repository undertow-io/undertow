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
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ChannelListener.Setter;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class StreamSourceChannelAdapter implements StreamSourceChannel {
    private final ReadableByteChannel channel;
    private final ChannelListener.SimpleSetter<? extends StreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<>();
    private final ChannelListener.SimpleSetter<? extends StreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<>();

    public StreamSourceChannelAdapter(ReadableByteChannel channel) {
        this.channel = channel;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
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
    public long read(ByteBuffer[] dst) throws IOException {
        return read(dst, 0, dst.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        return channel.read(dsts[0]);
    }

    @Override
    public void suspendReads() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeReads() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadResumed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wakeupReads() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownReads() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void awaitReadable() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    public XnioExecutor getReadThread() {
        throw new UnsupportedOperationException();
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
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) count);
        int r = channel.read(buf);
        buf.flip();
        while(buf.hasRemaining()) {
            if ( target.write(buf) < 1) {
                throw new IOException("Unable to write out all bytes");
            }
        }
        buf.clear();
        return r;
    }

    @Override
    public long transferTo(long count, ByteBuffer buf, StreamSinkChannel target) throws IOException {
        buf.flip();
        if (count < buf.remaining()) {
            buf.limit(buf.position() + (int) count);
        }
        int r = channel.read(buf);
        while(buf.hasRemaining()) {
            if ( target.write(buf) < 1) {
                throw new IOException("Unable to write out all bytes");
            }
        }
        return r;

    }

    @Override
    public Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    @Override
    public Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

}
