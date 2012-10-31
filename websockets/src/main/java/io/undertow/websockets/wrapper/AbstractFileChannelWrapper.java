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
package io.undertow.websockets.wrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 *
 * FileChannel implementation which wraps another FileChannel and delegate the operations to it.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class AbstractFileChannelWrapper extends FileChannel {
    protected final FileChannel channel;

    protected AbstractFileChannelWrapper(FileChannel channel) {
        this.channel = channel;
    }

    @Override
    public long position() throws IOException {
        return channel.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        return wrapFileChannel(channel.position(newPosition));
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public  FileChannel truncate(long size) throws IOException {
        return wrapFileChannel(channel.truncate(size));
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
        int r = channel.read(dst);
        afterReading(dst);
        return r;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long r = channel.read(dsts, offset, length);
        for (int i = offset; i < length; i++) {
            afterReading(dsts[i]);
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
        int r = channel.read(dst, position);
        afterReading(dst);
        return r;
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return channel.transferTo(position, count, wrapWritableByteChannel(target));
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        return channel.transferFrom(wrapReadableByteChannel(src) ,position, count);
    }

    /**
     * Is called before an actual write method is executed with the given ByteBuffer
     */
    protected abstract void beforeWriting(ByteBuffer buffer) throws IOException;


    /**
     * Is called after a read operation was executed with the given ByteBuffer
     */
    protected abstract void afterReading(ByteBuffer buffer) throws IOException;

    /**
     * Wrap the given ReadableByteChannel
     */
    protected abstract ReadableByteChannel wrapReadableByteChannel(ReadableByteChannel channel);

    /**
     * Wrap the given WritableByteChannel
     */
    protected abstract WritableByteChannel wrapWritableByteChannel(WritableByteChannel channel);

    /**
     * Wrap the given FileChannel
     */
    protected abstract AbstractFileChannelWrapper wrapFileChannel(FileChannel channel);
}
