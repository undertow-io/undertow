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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener.Setter;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * 
 * A {@link StreamSinkChannel} which will delegate all the operations to a specific {@link StreamSinkChannel} depending on the {@link WebSocketExchange}.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocketResponseDelegatorChannel implements StreamSinkChannel {

    private final StreamSinkChannel delegate;
    
    public WebSocketResponseDelegatorChannel(final StreamSinkChannel delegate, final Pool<ByteBuffer> pool, final WebSocketExchange exchange) {
        if (exchange.getRequestFrameType() == null) {
            throw new IllegalStateException("WebSocketFrameType must be set before try to write the payload");
        }

        // TODO: Add the needed code here for the different versions. First 00 to start easy
        switch (exchange.getVersion()) {
        default:
            throw new IllegalStateException("Unable to handle WebSocket version " + exchange.getVersion());
        }
    }

    @Override
    public int write(ByteBuffer arg0) throws IOException {
        return delegate.write(arg0);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return delegate.write(srcs);
    }

    @Override
    public long write(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
        return delegate.write(arg0, arg1, arg2);
    }

    @Override
    public void suspendWrites() {
        delegate.suspendWrites();
    }

    @Override
    public void resumeWrites() {
        delegate.resumeWrites();
    }

    @Override
    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    @Override
    public void wakeupWrites() {
        delegate.wakeupWrites();
    }

    @Override
    public void shutdownWrites() throws IOException {
        delegate.shutdownWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    @Override
    public boolean flush() throws IOException {
        return delegate.flush();
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return delegate.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return delegate.transferFrom(source, count, throughBuffer);
    }

    @Override
    public Setter<? extends StreamSinkChannel> getWriteSetter() {
        return delegate.getWriteSetter();
    }

    @Override
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return delegate.getCloseSetter();
    }

}
