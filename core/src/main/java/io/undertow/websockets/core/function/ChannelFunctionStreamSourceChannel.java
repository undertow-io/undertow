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
package io.undertow.websockets.core.function;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ChannelFunctionStreamSourceChannel implements StreamSourceChannel {
    private final StreamSourceChannel channel;
    private final ChannelFunction[] functions;

    public ChannelFunctionStreamSourceChannel(StreamSourceChannel channel, ChannelFunction... functions) {
        this.channel = channel;
        this.functions = functions;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return channel.transferTo(position, count, new ChannelFunctionFileChannel(target, functions));
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return target.transferFrom(this, count, throughBuffer);
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return channel.getReadSetter();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return channel.getCloseSetter();
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long r = 0;
        for (int a = offset; a < length; a++) {
            int i = read(dsts[a]);
            if (i < 1) {
                break;
            }
            r += i;
        }
        return r;
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        long r = 0;
        for (ByteBuffer buf: dsts) {
            int i = read(buf);
            if (i < 1) {
                break;
            }
            r += i;
        }
        return r;
    }

    @Override
    public void suspendReads() {
        channel.suspendReads();
    }

    @Override
    public void resumeReads() {
        channel.resumeReads();
    }

    @Override
    public boolean isReadResumed() {
        return channel.isReadResumed();
    }

    @Override
    public void wakeupReads() {
        channel.wakeupReads();
    }

    @Override
    public void shutdownReads() throws IOException {
        channel.shutdownReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        channel.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        channel.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioExecutor getReadThread() {
        return channel.getReadThread();
    }


    @Override
    public int read(ByteBuffer dst) throws IOException {
        int position = dst.position();
        int r = channel.read(dst);
        if (r > 0) {
            afterReading(dst, position, r);
        }
        return r;
    }


    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IOException {
        return channel.setOption(option, value);
    }

    private void afterReading(ByteBuffer buffer, int position, int length) throws IOException {
        for (ChannelFunction func: functions) {
            func.afterRead(buffer, position, length);
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
