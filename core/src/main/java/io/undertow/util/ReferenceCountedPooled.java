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

import io.undertow.UndertowMessages;
import org.xnio.Pooled;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author Stuart Douglas
 */
public class ReferenceCountedPooled<T> implements Pooled<T> {

    private final Pooled<T> underlying;
    @SuppressWarnings("unused")
    private volatile int referenceCount;
    private volatile boolean discard = false;
    boolean mainFreed = false;

    private static final AtomicIntegerFieldUpdater<ReferenceCountedPooled> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(ReferenceCountedPooled.class, "referenceCount");

    public ReferenceCountedPooled(Pooled<T> underlying, int referenceCount) {
        this.underlying = underlying;
        this.referenceCount = referenceCount;
    }

    @Override
    public void discard() {
        this.discard = true;
        if(referenceCountUpdater.decrementAndGet(this) == 0) {
            underlying.discard();
        }

    }

    @Override
    public void free() {
        if(mainFreed) {
            throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
        }
        mainFreed = true;
        freeInternal();
    }
    public void freeInternal() {
        if(referenceCountUpdater.decrementAndGet(this) == 0) {
            if(discard) {
                underlying.discard();
            } else {
                underlying.free();
            }
        }
    }

    @Override
    public T getResource() throws IllegalStateException {
        return underlying.getResource();
    }

    @Override
    public void close() {
        free();
    }

    public Pooled<T> createView(final T newValue) {
        increaseReferenceCount();
        return new Pooled<T>() {

            boolean free = false;

            @Override
            public void discard() {
                if(!free) {
                    free = true;
                    ReferenceCountedPooled.this.freeInternal();
                }
            }

            @Override
            public void free() {
                //make sure that a given view can only be freed once
                if(!free) {
                    free = true;
                    ReferenceCountedPooled.this.freeInternal();
                }
            }

            @Override
            public T getResource() throws IllegalStateException {
                if(free) {
                    throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
                }
                return newValue;
            }

            @Override
            public void close() {
                free();
            }
        };
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
}
