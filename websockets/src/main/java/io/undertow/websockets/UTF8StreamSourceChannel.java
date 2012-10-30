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
 * StreamSourceChannel which checks if all read / transfered data contains only UTF-8 bytes.
 * If non-UTF8 is detected it will throw an {@link java.io.UnsupportedEncodingException}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class UTF8StreamSourceChannel extends UTF8ReadableByteChannel implements StreamSourceChannel {
    private final StreamSourceChannel source;

    public UTF8StreamSourceChannel(StreamSourceChannel channel, UTF8Checker checker) {
        super(channel, checker);
        this.source = channel;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return source.transferTo(position, count, new UTF8FileChannel(target, checker));
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return source.transferTo(count, throughBuffer, new UTF8StreamSinkChannel(target, checker));
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return source.getReadSetter();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return source.getCloseSetter();
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
        source.suspendReads();
    }

    @Override
    public void resumeReads() {
       source.resumeReads();
    }

    @Override
    public boolean isReadResumed() {
        return source.isReadResumed();
    }

    @Override
    public void wakeupReads() {
        source.wakeupReads();
    }

    @Override
    public void shutdownReads() throws IOException {
        source.shutdownReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        source.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        source.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioExecutor getReadThread() {
        return source.getReadThread();
    }

    @Override
    public XnioWorker getWorker() {
        return source.getWorker();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return source.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return source.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return source.setOption(option, value);
    }
}
