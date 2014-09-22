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

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class SingleByteStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final int singleByteReads;

    private int state = 0;


    /**
     * Construct a new instance.
     *
     * @param next            the delegate conduit to set
     * @param singleByteReads
     */
    public SingleByteStreamSourceConduit(StreamSourceConduit next, int singleByteReads) {
        super(next);
        this.singleByteReads = singleByteReads;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (state > singleByteReads || dst.remaining() == 1) {
            //we always let a single byte read through, otherwise SSL renegotiation breaks
            return next.read(dst);
        }

        if (state++ % 2 == 0) {
            wakeupIfSsl();
            return 0;
        } else {
            if (dst.remaining() == 0) {
                return 0;
            }
            int limit = dst.limit();
            try {
                dst.limit(dst.position() + 1);
                int read = next.read(dst);
                if(read != -1) {
                    wakeupIfSsl();
                }
                return read;
            } finally {
                dst.limit(limit);
            }
        }
    }

    private void wakeupIfSsl() {
        //todo: work around a bug in the SSL channel where the read listener will not be invoked if there is more data in the buffer
        if(isReadResumed() && next.getClass().getSimpleName().startsWith("Jsse")) {
            wakeupReads();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        if (state > singleByteReads) {
            return next.read(dsts, offs, len);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            ByteBuffer dst = null;
            for (int i = offs; i < offs + len; ++i) {
                if (dsts[i].hasRemaining()) {
                    dst = dsts[i];
                    break;
                }
            }
            if (dst == null) {
                return 0;
            }
            int limit = dst.limit();
            try {
                dst.limit(dst.position() + 1);
                return next.read(dst);
            } finally {
                dst.limit(limit);
            }
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (state > singleByteReads) {
            return next.transferTo(position, count, target);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            return next.transferTo(position, count == 0 ? 0 : count, target);
        }
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (state > singleByteReads) {
            return next.transferTo(count, throughBuffer, target);
        }
        if (state++ % 2 == 0) {
            throughBuffer.position(throughBuffer.limit());
            return 0;
        } else {
            return next.transferTo(count == 0 ? 0 : count, throughBuffer, target);
        }
    }
}
