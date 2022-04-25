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

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;

/**
 * A reference counted pooled implementation, that basically consists of a main buffer, that can be sliced off into smaller buffers,
 * and the underlying buffer will not be freed until all the slices and the main buffer itself have also been freed.
 *
 * This also supports the notion of un-freeing the main buffer. Basically this allows the buffer be re-used, so if only a small slice of the
 * buffer was used for read operations the main buffer can potentially be re-used. This prevents buffer exhaustion attacks where content
 * is sent in many small packets, and you end up allocating a large number of buffers to hold a small amount of data.
 *
 * @author Stuart Douglas
 */
public class ReferenceCountedPooled implements PooledByteBuffer {

    private final PooledByteBuffer underlying;
    @SuppressWarnings("unused")
    private volatile int referenceCount;
    boolean mainFreed = false;
    private ByteBuffer slice = null;
    private final FreeNotifier freeNotifier;

    private static final AtomicIntegerFieldUpdater<ReferenceCountedPooled> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(ReferenceCountedPooled.class, "referenceCount");

    public ReferenceCountedPooled(PooledByteBuffer underlying, int referenceCount) {
        this(underlying, referenceCount, null);
    }

    public ReferenceCountedPooled(PooledByteBuffer underlying, int referenceCount, FreeNotifier freeNotifier) {
        this.underlying = underlying;
        this.referenceCount = referenceCount;
        this.freeNotifier = freeNotifier;
    }

    @Override
    public void close() {
        if(mainFreed) {
            return;
        }
        mainFreed = true;
        freeInternal();
    }

    @Override
    public boolean isOpen() {
        return !mainFreed;
    }

    public boolean isFreed() {
        return mainFreed;
    }

    public boolean tryUnfree() {
        int refs;
        do {
            refs  = referenceCountUpdater.get(this);
            if(refs <= 0) {
                 return false;
            }
        } while (!referenceCountUpdater.compareAndSet(this, refs, refs + 1));
        ByteBuffer resource = slice != null ? slice : underlying.getBuffer();
        resource.position(resource.limit());
        resource.limit(resource.capacity());
        slice = resource.slice();
        mainFreed = false;
        return true;
    }

    private void freeInternal() {
        if(referenceCountUpdater.decrementAndGet(this) == 0) {
            underlying.close();
            if(freeNotifier != null) {
                freeNotifier.freed();
            }
        }
    }

    @Override
    public ByteBuffer getBuffer() throws IllegalStateException {
        if(mainFreed) {
            throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
        }
        if(slice != null) {
            return slice;
        }
        return underlying.getBuffer();
    }

    public PooledByteBuffer createView(int viewSize) {
        ByteBuffer newView = getBuffer().duplicate();
        newView.limit(newView.position() + viewSize);
        final ByteBuffer newValue = newView.slice();
        ByteBuffer newUnderlying = getBuffer().duplicate();
        newUnderlying.position(newUnderlying.position() + viewSize);

        int oldRemaining = newUnderlying.remaining();
        newUnderlying.limit(newUnderlying.capacity());
        newUnderlying = newUnderlying.slice();
        newUnderlying.limit(newUnderlying.position() + oldRemaining);
        slice = newUnderlying;

        increaseReferenceCount();
        return new PooledByteBuffer() {

            volatile boolean free = false;

            @Override
            public void close() {
                //make sure that a given view can only be freed once
                if(!free) {
                    synchronized (this) {
                        if (free) return;
                        free = true;
                        ReferenceCountedPooled.this.freeInternal();
                    }
                }
            }

            @Override
            public boolean isOpen() {
                return !free;
            }

            @Override
            public ByteBuffer getBuffer() throws IllegalStateException {
                if(free) {
                    throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
                }
                return newValue;
            }

            @Override
            public String toString() {
                return "ReferenceCountedPooled$view{" +
                        "buffer=" + newValue +
                        "free=" + free +
                        "underlying=" + underlying +
                        ", referenceCount=" + referenceCount +
                        ", mainFreed=" + mainFreed +
                        ", slice=" + slice +
                        '}';
            }
        };
    }

    public PooledByteBuffer createView() {
        return createView(getBuffer().remaining());
    }

    public void increaseReferenceCount() {
        int val;
        do {
            val = referenceCountUpdater.get(this);
            if(val == 0) {
                //should never happen, as this should only be called from
                //code that already has a reference
                throw UndertowMessages.MESSAGES.objectWasFreed();
            }
        } while (!referenceCountUpdater.compareAndSet(this, val, val + 1));
    }

    public interface FreeNotifier {
        void freed();
    }

    @Override
    public String toString() {
        return "ReferenceCountedPooled{" +
                "underlying=" + underlying +
                ", referenceCount=" + referenceCount +
                ", mainFreed=" + mainFreed +
                ", slice=" + slice +
                '}';
    }
}
