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

package io.undertow.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.XnioByteBufferPool;
import io.undertow.websockets.core.UTF8Output;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import org.xnio.Pool;
import org.xnio.channels.StreamSourceChannel;

/**
 * Simple utility class for reading a string
 * <p>
 * todo: handle unicode properly
 *
 * @author Stuart Douglas
 */
public abstract class StringReadChannelListener implements ChannelListener<StreamSourceChannel> {

    private final UTF8Output string = new UTF8Output();
    private final ByteBufferPool bufferPool;

    public StringReadChannelListener(final ByteBufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    @Deprecated
    public StringReadChannelListener(final Pool<ByteBuffer> bufferPool) {
        this.bufferPool = new XnioByteBufferPool(bufferPool);
    }

    public void setup(final StreamSourceChannel channel) {
        PooledByteBuffer resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getBuffer();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    channel.getReadSetter().set(this);
                    channel.resumeReads();
                } else if (r == -1) {
                    stringDone(string.extract());
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    string.write(buffer);
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.close();
        }
    }

    @Override
    public void handleEvent(final StreamSourceChannel channel) {
        PooledByteBuffer resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getBuffer();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    return;
                } else if (r == -1) {
                    stringDone(string.extract());
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    string.write(buffer);
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.close();
        }
    }

    protected abstract void stringDone(String string);

    protected abstract void error(IOException e);
}
