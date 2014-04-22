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
 * A conduit that calls a finish listener when there is no data left in the underlying conduit.
 *
 * @author Stuart Douglas
 */
public final class FinishableStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final ConduitListener<? super FinishableStreamSourceConduit> finishListener;

    private boolean finishCalled = false;

    public FinishableStreamSourceConduit(final StreamSourceConduit next, final ConduitListener<? super FinishableStreamSourceConduit> finishListener) {
        super(next);
        this.finishListener = finishListener;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long res = 0;
        try {
            return res = next.transferTo(position, count, target);
        } finally {
            exitRead(res);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        long res = 0;
        try {
            return res = next.transferTo(count, throughBuffer, target);
        } finally {
            exitRead(res);
        }
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long res = 0;
        try {
            return res = next.read(dsts, offset, length);
        } finally {
            exitRead(res);
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        int res = 0;
        try {
            return res = next.read(dst);
        } finally {
            exitRead(res);
        }
    }

    /**
     * Exit a read method.
     *
     * @param consumed the number of bytes consumed by this call (may be 0)
     */
    private void exitRead(long consumed) {
        if (consumed == -1) {
            if (!finishCalled) {
                finishCalled = true;
                finishListener.handleEvent(this);
            }
        }
    }
}
