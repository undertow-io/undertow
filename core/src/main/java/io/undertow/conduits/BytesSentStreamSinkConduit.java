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

import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class BytesSentStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final ByteActivityCallback callback;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param callback
     */
    public BytesSentStreamSinkConduit(StreamSinkConduit next, ByteActivityCallback callback) {
        super(next);
        this.callback = callback;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        long l = next.transferFrom(src, position, count);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        long l = next.transferFrom(source, count, throughBuffer);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int i = next.write(src);
        if (i > 0) {
            callback.activity(i);
        }
        return i;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        long l = next.write(srcs, offs, len);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int i = next.writeFinal(src);
        if (i > 0) {
            callback.activity(i);
        }
        return i;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long l = next.writeFinal(srcs, offset, length);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }
}
