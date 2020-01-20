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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.util.WorkerUtils;

/**
 * Class that implements the token bucket algorithm.
 * <p>
 * Allows send speed to be throttled
 * <p>
 * Note that throttling is applied after an initial write, so if a big write is performed initially
 * it may be a while before it can write again.
 *
 * @author Stuart Douglas
 */
public class RateLimitingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final long time;
    private final int bytes;
    private boolean writesResumed = false;

    private int byteCount = 0;
    private long startTime = 0;
    private long nextSendTime = 0;

    private boolean scheduled = false;

    /**
     * @param next     The next conduit
     * @param bytes    The number of bytes that are allowed per time frame
     * @param time     The time frame
     * @param timeUnit The time unit
     */
    public RateLimitingStreamSinkConduit(StreamSinkConduit next, int bytes, long time, TimeUnit timeUnit) {
        super(next);
        writesResumed = next.isWriteResumed();
        this.time = timeUnit.toMillis(time);
        this.bytes = bytes;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int bytes = this.bytes - this.byteCount;
        int old = src.limit();
        if (src.remaining() > bytes) {
            src.limit(src.position() + bytes);
        }
        try {
            int written = super.write(src);
            handleWritten(written);
            return written;
        } finally {
            src.limit(old);
        }
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int bytes = this.bytes - this.byteCount;
        long written = super.transferFrom(src, position, Math.min(count, bytes));
        handleWritten(written);
        return written;
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int bytes = this.bytes - this.byteCount;
        long written = super.transferFrom(source, Math.min(count, bytes), throughBuffer);
        handleWritten(written);
        return written;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int old = 0;
        int adjPos = -1;
        long rem = bytes - byteCount;
        for (int i = offs; i < offs + len; ++i) {
            ByteBuffer buf = srcs[i];
            rem -= buf.remaining();
            if (rem < 0) {
                adjPos = i;
                old = buf.limit();
                buf.limit((int) (buf.limit() + rem));
                break;
            }
        }
        try {
            long written;
            if (adjPos == -1) {
                written = super.write(srcs, offs, len);
            } else {
                written = super.write(srcs, offs, adjPos - offs + 1);
            }
            handleWritten(written);
            return written;
        } finally {
            if (adjPos != -1) {
                ByteBuffer buf = srcs[adjPos];
                buf.limit(old);
            }
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int bytes = this.bytes - this.byteCount;
        int old = src.limit();
        if (src.remaining() > bytes) {
            src.limit(src.position() + bytes);
        }
        try {
            int written = super.writeFinal(src);
            handleWritten(written);
            return written;
        } finally {
            src.limit(old);
        }
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (!canSend()) {
            return 0;
        }
        int old = 0;
        int adjPos = -1;
        long rem = bytes - byteCount;
        for (int i = offs; i < offs + len; ++i) {
            ByteBuffer buf = srcs[i];
            rem -= buf.remaining();
            if (rem < 0) {
                adjPos = i;
                old = buf.limit();
                buf.limit((int) (buf.limit() + rem));
                break;
            }
        }
        try {
            long written;
            if (adjPos == -1) {
                written = super.writeFinal(srcs, offs, len);
            } else {
                written = super.writeFinal(srcs, offs, adjPos - offs + 1);
            }
            handleWritten(written);
            return written;
        } finally {
            if (adjPos != -1) {
                ByteBuffer buf = srcs[adjPos];
                buf.limit(old);
            }
        }
    }

    @Override
    public void resumeWrites() {
        writesResumed = true;
        if (canSend()) {
            super.resumeWrites();
        }
    }

    @Override
    public void suspendWrites() {
        writesResumed = false;
        super.suspendWrites();
    }

    @Override
    public void wakeupWrites() {
        writesResumed = true;
        if (canSend()) {
            super.wakeupWrites();
        }
    }

    @Override
    public boolean isWriteResumed() {
        return writesResumed;
    }

    @Override
    public void awaitWritable() throws IOException {
        long toGo = nextSendTime - System.currentTimeMillis();
        if (toGo > 0) {
            try {
                Thread.sleep(toGo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
        super.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {

        long toGo = nextSendTime - System.currentTimeMillis();
        if (toGo > 0) {
            try {
                Thread.sleep(Math.min(toGo, timeUnit.toMillis(time)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            return;
        }
        super.awaitWritable(time, timeUnit);
    }

    private boolean canSend() {
        if (byteCount < bytes) {
            return true;
        }
        if (System.currentTimeMillis() > nextSendTime) {
            byteCount = 0;
            startTime = 0;
            nextSendTime = 0;
            return true;
        }
        if (writesResumed) {
            handleWritesResumedWhenBlocked();
        }
        return false;
    }

    private void handleWritten(long written) {
        if (written == 0) {
            return;
        }
        byteCount += written;
        if (byteCount < bytes) {
            //we are still allowed to send
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
                nextSendTime = System.currentTimeMillis() + time;
            }
        } else {
            //we have gone over, we need to wait till we are allowed to send again
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }
            nextSendTime = startTime + time;
            if (writesResumed) {
                handleWritesResumedWhenBlocked();
            }
        }
    }

    private void handleWritesResumedWhenBlocked() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        next.suspendWrites();
        long millis = nextSendTime - System.currentTimeMillis();
        WorkerUtils.executeAfter(getWriteThread(), new Runnable() {
            @Override
            public void run() {
                scheduled = false;
                if (writesResumed) {
                    next.wakeupWrites();
                }
            }
        }, millis, TimeUnit.MILLISECONDS);
    }

}
