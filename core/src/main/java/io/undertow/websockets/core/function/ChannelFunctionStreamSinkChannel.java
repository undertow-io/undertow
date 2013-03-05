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
package io.undertow.websockets.core.function;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ChannelFunctionStreamSinkChannel implements StreamSinkChannel {
    private final StreamSinkChannel channel;
    private final ChannelFunction[] functions;

    public ChannelFunctionStreamSinkChannel(StreamSinkChannel channel, ChannelFunction... functions) {
        this.channel = channel;
        this.functions = functions;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return channel.getWriteSetter();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return channel.getCloseSetter();
    }

    @Override
    public void suspendWrites() {
        channel.suspendWrites();
    }

    @Override
    public void resumeWrites() {
        channel.resumeWrites();
    }

    @Override
    public boolean isWriteResumed() {
        return channel.isWriteResumed();
    }

    @Override
    public void wakeupWrites() {
        channel.wakeupWrites();
    }

    @Override
    public void shutdownWrites() throws IOException {
        channel.shutdownWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        channel.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        channel.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }

    @Override
    public boolean flush() throws IOException {
        return channel.flush();
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

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return channel.transferFrom(new ChannelFunctionFileChannel(src, functions), position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return channel.transferFrom(new ChannelFunctionStreamSourceChannel(source, functions), count, throughBuffer);
    }


    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        for (int i = offset; i < length; i++) {
            ByteBuffer src = srcs[i];
            beforeWriting(src);
        }
        return channel.write(srcs, offset, length);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        for (ByteBuffer src: srcs) {
            beforeWriting(src);
        }
        return channel.write(srcs);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        beforeWriting(src);
        return channel.write(src);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private void beforeWriting(ByteBuffer buffer) throws IOException {
        for (ChannelFunction func: functions) {
            int pos = buffer.position();
            func.beforeWrite(buffer, pos, buffer.limit() - pos);
        }
    }

}
