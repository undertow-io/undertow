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
package io.undertow.websockets.utf8;

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
public final class UTF8FileChannel extends FileChannel {
    private final FileChannel fc;
    private final UTF8Checker checker;

    public UTF8FileChannel(FileChannel fc, UTF8Checker checker) {
        this.fc = fc;
        this.checker = checker;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int r = fc.read(dst);

        checker.checkUTF8(dst, pos, r);
        return r;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checker.checkUTF8(src, src.position(), src.limit());
        return fc.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        for (int i = offset; i < length; i++) {
            ByteBuffer src = srcs[i];
            checker.checkUTF8(src, src.position(), src.limit());
        }
        return fc.write(srcs, offset, length);
    }

    @Override
    public long position() throws IOException {
        return fc.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        return new UTF8FileChannel(fc.position(newPosition), checker);
    }

    @Override
    public long size() throws IOException {
        return fc.size();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        return new UTF8FileChannel(fc.truncate(size), checker);
    }

    @Override
    public void force(boolean metaData) throws IOException {
        fc.force(metaData);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return fc.transferTo(position, count, new UTF8WritableByteChannel(target, checker));
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        return fc.transferFrom(new UTF8ReadableByteChannel(src, checker), position, count);
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        int pos = dst.position();
        int r = fc.read(dst, position);

        checker.checkUTF8(dst, pos, r);
        return r;
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
    public int write(ByteBuffer src, long position) throws IOException {
        for (int i = src.position(); i < src.limit(); i++) {
            checker.checkUTF8(src.get(i));
        }
        return fc.write(src, position);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        return fc.map(mode, position, size);
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return fc.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return fc.tryLock(position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
        fc.close();
    }
}