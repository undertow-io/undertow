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

package io.undertow.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public class ChunkedChannel implements StreamSinkChannel {


    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return 0;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return 0;
    }

    @Override
    public void suspendWrites() {

    }

    @Override
    public void resumeWrites() {

    }

    @Override
    public boolean isWriteResumed() {
        return false;
    }

    @Override
    public void wakeupWrites() {

    }

    @Override
    public void shutdownWrites() throws IOException {

    }

    @Override
    public void awaitWritable() throws IOException {

    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {

    }

    @Override
    public XnioExecutor getWriteThread() {
        return null;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return null;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return null;
    }

    @Override
    public XnioWorker getWorker() {
        return null;
    }

    @Override
    public boolean flush() throws IOException {
        return false;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return 0;
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        return 0;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return 0;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }
}
