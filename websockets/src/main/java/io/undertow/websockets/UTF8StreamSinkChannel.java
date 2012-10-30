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
package io.undertow.websockets;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
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
public class UTF8StreamSinkChannel extends UTF8WritableByteChannel implements StreamSinkChannel {

    private final StreamSinkChannel sink;

    public UTF8StreamSinkChannel(StreamSinkChannel channel, UTF8Checker checker) {
        super(channel, checker);
        this.sink = channel;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return sink.transferFrom(new UTF8FileChannel(src, checker), position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return sink.transferFrom(new UTF8StreamSourceChannel(source, checker), count, throughBuffer);
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return sink.getWriteSetter();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return sink.getCloseSetter();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        for (int i = offset; i < length; i++) {
            ByteBuffer src = srcs[i];
            checker.checkUTF8(src, src.position(), src.limit());
        }
        return sink.write(srcs, offset, length);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        for (ByteBuffer src: srcs) {
            checker.checkUTF8(src, src.position(), src.limit());
        }
        return sink.write(srcs);
    }

    @Override
    public void suspendWrites() {
        sink.suspendWrites();
    }

    @Override
    public void resumeWrites() {
        sink.resumeWrites();
    }

    @Override
    public boolean isWriteResumed() {
        return sink.isWriteResumed();
    }

    @Override
    public void wakeupWrites() {
        sink.wakeupWrites();
    }

    @Override
    public void shutdownWrites() throws IOException {
        sink.shutdownWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        sink.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        sink.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        return sink.getWriteThread();
    }

    @Override
    public boolean flush() throws IOException {
        return sink.flush();
    }

    @Override
    public XnioWorker getWorker() {
        return sink.getWorker();
    }


    @Override
    public boolean supportsOption(Option<?> option) {
        return sink.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return sink.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return sink.setOption(option, value);
    }
}
