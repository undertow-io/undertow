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

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class BytesReceivedStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final ByteActivityCallback callback;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param callback
     */
    public BytesReceivedStreamSourceConduit(StreamSourceConduit next, ByteActivityCallback callback) {
        super(next);
        this.callback = callback;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        long l = super.transferTo(position, count, target);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        long l = super.transferTo(count, throughBuffer, target);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int i = super.read(dst);
        if (i > 0) {
            callback.activity(i);
        }
        return i;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        long l = super.read(dsts, offs, len);
        if (l > 0) {
            callback.activity(l);
        }
        return l;
    }
}
