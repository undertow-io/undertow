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

import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A channel that will only write a single byte at a time for a set number of calls to write.
 *
 * This can be used for testing purposes, to make sure that resuming writes works as expected.
 *
 * @author Stuart Douglas
 */
public class SingleByteStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final int singleByteWrites;

    private int state = 0;

    /**
     * Construct a new instance.
     *
     * @param next             the delegate conduit to set
     * @param singleByteWrites
     */
    public SingleByteStreamSinkConduit(StreamSinkConduit next, int singleByteWrites) {
        super(next);
        this.singleByteWrites = singleByteWrites;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (state > singleByteWrites) {
            return next.write(src);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            if (src.remaining() == 0) {
                return 0;
            }
            int limit = src.limit();
            try {
                src.limit(src.position() + 1);
                return next.write(src);
            } finally {
                src.limit(limit);
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (state > singleByteWrites) {
            return next.write(srcs, offs, len);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            ByteBuffer src = null;
            for(int i = offs; i < offs + len; ++i) {
                if(srcs[i].hasRemaining()) {
                    src = srcs[i];
                    break;
                }
            }
            if(src == null) {
                return 0;
            }
            int limit = src.limit();
            try {
                src.limit(src.position() + 1);
                return next.write(src);
            } finally {
                src.limit(limit);
            }
        }
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (state > singleByteWrites) {
            return next.transferFrom(src, position, count);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            return next.transferFrom(src, position, count == 0 ? 0 : 1);
        }
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (state > singleByteWrites) {
            return next.transferFrom(source, count, throughBuffer);
        }
        if (state++ % 2 == 0) {
            throughBuffer.limit(throughBuffer.position());
            return 0;
        } else {
            return next.transferFrom(source, count == 0 ? 0 : 1, throughBuffer);
        }
    }
}
