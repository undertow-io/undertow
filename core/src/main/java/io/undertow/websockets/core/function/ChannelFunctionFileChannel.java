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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ChannelFunctionFileChannel extends FileChannel  {
    private final ChannelFunction[] functions;
    private final FileChannel channel;

    public ChannelFunctionFileChannel(FileChannel channel, ChannelFunction... functions) {
        this.channel = channel;
        this.functions = functions;
    }

    @Override
    public long position() throws IOException {
        return channel.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        channel.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public  FileChannel truncate(long size) throws IOException {
        channel.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        channel.force(metaData);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        return channel.map(mode, position, size);
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return channel.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return channel.tryLock(position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
        channel.close();
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        beforeWriting(src);
        return channel.write(src, position);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int r = channel.read(dst);
        if (r > 0) {
            afterReading(dst, pos, r);
        }
        return r;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int[] positions = new int[length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = dsts[i].position();
        }
        long r = channel.read(dsts, offset, length);
        if (r > 0) {
            for (int i = offset; i < length; i++) {
                ByteBuffer dst = dsts[i];
                afterReading(dst, positions[i], dst.position());
            }
        }
        return r;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        beforeWriting(src);
        return channel.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        for (int i = offset; i < length; i++) {
            beforeWriting(srcs[i]);
        }
        return channel.write(srcs, offset, length);
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        int pos = dst.position();
        int r = channel.read(dst, position);
        if (r > 0) {
            afterReading(dst, pos, r);
        }
        return r;
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return channel.transferTo(position, count, new ChannelFunctionWritableByteChannel(target, functions));
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        return channel.transferFrom(new ChannelFunctionReadableByteChannel(channel, functions) ,position, count);
    }


    private void beforeWriting(ByteBuffer buffer) throws IOException {
        for (ChannelFunction func: functions) {
            int pos = buffer.position();
            func.beforeWrite(buffer, pos, buffer.limit() - pos);
        }
    }

    private void afterReading(ByteBuffer buffer, int position, int length) throws IOException {
        for (ChannelFunction func: functions) {
            func.afterRead(buffer, position, length);
        }
    }

}
