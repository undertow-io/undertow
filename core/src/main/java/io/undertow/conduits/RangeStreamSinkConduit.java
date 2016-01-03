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

package io.undertow.conduits;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class RangeStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final long start, end;
    private final long originalResponseLength;

    private long written;

    public RangeStreamSinkConduit(StreamSinkConduit next, long start, long end, long originalResponseLength) {
        super(next);
        this.start = start;
        this.end = end;
        this.originalResponseLength = originalResponseLength;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        boolean currentInclude = written >= start && written <= end;
        long bytesRemaining = written < start ? start - written : written <= end ? end - written + 1 : Long.MAX_VALUE;
        if (currentInclude) {
            int old = src.limit();
            src.limit((int) Math.min(src.position() + bytesRemaining, src.limit()));
            int written;
            int toConsume = 0;
            try {
                written = super.write(src);
                this.written += written;
            } finally {
                if (!src.hasRemaining()) {
                    //we wrote everything out
                    src.limit(old);
                    if (src.hasRemaining()) {
                        toConsume = src.remaining();
                        //but there was still some data that fell outside the range, so we discard it
                        this.written += toConsume;
                        src.position(src.limit());
                    }
                } else {
                    src.limit(old);
                }
            }
            return written + toConsume;
        } else {
            if (src.remaining() <= bytesRemaining) {
                int rem = src.remaining();
                this.written += rem;
                src.position(src.limit());
                return rem;
            } else {
                this.written += bytesRemaining;
                src.position((int) (src.position() + bytesRemaining));
                return (int) bytesRemaining + write(src);
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        long ret = 0;
        //todo: a more efficent impl
        for (int i = offs; i < offs + len; ++i) {
            ByteBuffer buf = srcs[i];
            if (buf.remaining() > 0) {
                ret += write(buf);
                if (buf.hasRemaining()) {
                    return ret;
                }
            }
        }
        return ret;

    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }
}
