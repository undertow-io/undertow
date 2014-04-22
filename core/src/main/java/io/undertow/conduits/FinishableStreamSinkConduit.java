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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.xnio.Buffers;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FinishableStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {
    private final ConduitListener<? super FinishableStreamSinkConduit> finishListener;

    //0 = open
    //1 = writes shutdown
    //2 = finish listener invoked
    private int shutdownState = 0;

    public FinishableStreamSinkConduit(final StreamSinkConduit delegate, final ConduitListener<? super FinishableStreamSinkConduit> finishListener) {
        super(delegate);
        this.finishListener = finishListener;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int res = next.writeFinal(src);
        if(!src.hasRemaining()) {
            if (shutdownState == 0) {
                shutdownState = 1;
            }
        }
        return res;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long res = next.writeFinal(srcs, offset, length);
        if(!Buffers.hasRemaining(srcs, offset, length)) {
            if (shutdownState == 0) {
                shutdownState = 1;
            }
        }
        return res;
    }

    public void terminateWrites() throws IOException {
        super.terminateWrites();
        if (shutdownState == 0) {
            shutdownState = 1;
        }
    }

    @Override
    public void truncateWrites() throws IOException {
        next.truncateWrites();
        if (shutdownState != 2) {
            shutdownState = 2;
            finishListener.handleEvent(this);
        }
    }

    public boolean flush() throws IOException {
        final boolean val = next.flush();
        if (val && shutdownState == 1) {
            shutdownState = 2;
            finishListener.handleEvent(this);
        }
        return val;
    }
}
