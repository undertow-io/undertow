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

package io.undertow.websockets.extensions;

import java.nio.ByteBuffer;

import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.Pooled;

/**
 * A wrapper for {@link ByteBuffer} class used in extensions context.
 * <p>
 * An extension can transform buffer content beyond capacity.
 * <p>
 * This wrapper is a mechanism to allocate extra buffers in an automatic way.
 * <p>
 * {@code ExtensionByteBuffer} stores an internal array of {@link Pooled} buffer to manage an overflow of input {@link ByteBuffer} .
 *
 * @author Lucas Ponce
 */
public class ExtensionByteBuffer {
    private WebSocketChannel channel;

    private ByteBuffer input;
    private int currentPosition;

    private int extraBuffers;
    private Pooled<ByteBuffer>[] extraPools;

    private int filled;

    private boolean flushed;
    private int flushedBuffer;
    private int flushedPosition;

    /**
     * Create a new {@code ExtensionByteBuffer} instance wrapping a {@code ByteBuffer} .
     * <p>
     * It has access to {@link WebSocketChannel} instance to create extra buffer from {@link WebSocketChannel#getBufferPool()} .
     *
     * @param channel       the {@link WebSocketChannel} used on this constructor
     * @param input         the {@link ByteBuffer} to wrap on
     * @param initPosition  the index in the {@link ExtensionByteBuffer} to start from
     */
    public ExtensionByteBuffer(WebSocketChannel channel, ByteBuffer input, int initPosition) {
        this.channel = channel;
        this.input = input;
        this.currentPosition = initPosition;
        this.extraBuffers = 0;
        this.filled = 0;
        this.flushed = false;
        this.flushedBuffer = 0;
        this.flushedPosition = 0;
    }

    /**
     * Write the given byte into the wrapped {@link ByteBuffer} at the current position.
     * <p>
     * It creates an extra buffer when current position reaches wrapped buffer or previously extra buffer maximum capacity.
     *
     * @param value the byte to be written
     */
    public void put(byte value) {
        checkPosition();
        currentBuffer().put(currentPosition, value);
        currentPosition++;
        currentBuffer().position(currentPosition);
        filled++;
    }

    /**
     * Read the byte at the given position of wrapped buffer or extra buffers.
     *
     * @param  position
     *         The position from which the byte will be read
     *
     * @return  The byte at the given position
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>position</tt> is negative
     *          or not smaller than the buffer's limit or extra buffer's limit
     */
    public byte get(int position) {
        int relativePosition = getPosition(position);
        return getBuffer(position).get(relativePosition);
    }

    /**
     * Read the number of bytes filled on a {@link ExtensionByteBuffer#put(byte)} operation.
     *
     * @return the number of filled bytes in the buffer
     */
    public int getFilled() {
        return filled;
    }

    /**
     * Check if this instance has not flushed extra buffers.
     *
     * @return {@code true} if this wrapped buffer has extra buffers
     */
    public boolean hasExtra() {
        return extraBuffers > 0 && !flushed;
    }

    /**
     * Read number of extra buffers.
     *
     * @return the number of extra buffers
     */
    public int getExtra() {
        return extraBuffers;
    }

    /**
     * Get extra buffer at specified position.
     *
     * @param buffer the index of extra buffer
     * @return       the extra buffer at given position;
     *               {@code null} if no extra buffer or bad index specified
     */
    public ByteBuffer getExtraBuffer(int buffer) {
        if (extraBuffers == 0 || buffer < 0 || buffer >= extraBuffers) {
            return null;
        }
        return extraPools[buffer].getResource();
    }

    /**
     * Tells whether there are any elements between the current position and
     * the limit of extra buffers.
     *
     * @return  <tt>true</tt> if, and only if, there is at least one element
     *          remaining in this buffer
     */
    public boolean hasExtraRemaining() {
        if (extraBuffers == 0) {
            return false;
        } else {
            for (int i = 0; i < extraBuffers; i++) {
                if (extraPools[i].getResource().hasRemaining()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Retrieve first extra buffer with remaining bytes.
     *
     * @return the first extra buffer with condition {@code hasRemaining() == true};
     *         {@code null} if not extra buffers.
     */
    public ByteBuffer getExtraRemainingBuffer() {
        if (extraBuffers == 0) {
            return null;
        } else {
            for (int i = 0; i < extraBuffers; i++) {
                if (extraPools[i].getResource().hasRemaining()) {
                    return extraPools[i].getResource();
                }
            }
            return null;
        }
    }

    /**
     * Flip all extra buffers.
     */
    public void flipExtra() {
        if (extraBuffers == 0) {
            return;
        } else {
            for (int i = 0; i < extraBuffers; i++) {
                extraPools[i].getResource().flip();
            }
        }
    }

    /**
     * Copy extra buffers content into {@code ByteBuffer} passed as parameter.
     * <p>
     * When all content is flushed {@code hasExtra() == false} .
     *
     * @param dst target buffer to copy internal extra buffer content
     * @return the number of bytes flushed
     */
    public int flushExtra(ByteBuffer dst) {
        if (dst == null) throw new IndexOutOfBoundsException("ByteBuffer destination is empty");
        if (extraBuffers == 0) return 0;

        int count = 0;
        int maxPosition = 0;
        for ( ; flushedBuffer < extraBuffers; flushedBuffer++) {
            maxPosition =  ((flushedBuffer + 1) == extraBuffers) ? currentPosition : extraPools[flushedBuffer].getResource().capacity();
            for ( ; flushedPosition < maxPosition; flushedPosition++) {
                dst.put(extraPools[flushedBuffer].getResource().get(flushedPosition));
                count++;
                if (!dst.hasRemaining()) {
                    /*
                        Check if we are in EOF of extra buffers
                     */
                    if (flushedPosition == (maxPosition - 1)) {
                        if (flushedBuffer == (extraBuffers - 1)) {
                            /*
                                We have reached end of overflow buffers
                             */
                            flushed = true;
                            free();
                        } else {
                            flushedPosition = 0;
                            flushedBuffer++;
                        }
                    }
                    return count;
                }
            }
            flushedPosition = 0;
        }

        flushed = true;
        free();

        return count;
    }

    /**
     * Release extra pooled allocated for overflow content.
     */
    public void free() {
        if (extraPools != null) {
            for (int i = 0; i < extraPools.length; i++) {
                extraPools[i].free();
            }
        }
    }

    private void extraBuffer() {
        Pooled<ByteBuffer> extraBuffer = channel.getBufferPool().allocate();
        if (extraPools == null) {
            extraPools = new Pooled[1];
            extraPools[0] = extraBuffer;
            extraBuffers = 1;
        } else {
            Pooled<ByteBuffer>[] newExtraPools = new Pooled[extraBuffers + 1];
            for (int i = 0; i < extraBuffers; i++) {
                newExtraPools[i] = extraPools[i];
            }
            newExtraPools[extraBuffers] = extraBuffer;
            extraPools = newExtraPools;
            extraBuffers++;
        }
    }

    private void checkPosition() {
        if (currentPosition == currentBuffer().capacity()) {
            extraBuffer();
            currentPosition = 0;
        }
        if (currentPosition >= currentBuffer().limit()) {
            currentBuffer().limit(currentPosition + 1);
        }
    }

    private ByteBuffer currentBuffer() {
        if (extraBuffers == 0) {
            return input;
        } else {
            return extraPools[extraBuffers - 1].getResource();
        }
    }

    private ByteBuffer getBuffer(int position) {
        if (extraBuffers == 0) {
            return input;
        } else {
            if (position < input.capacity()) {
                return input;
            } else {
                int offset = input.capacity();
                for (int i = 0; i < extraPools.length; i++) {
                    if (position < (offset + extraPools[i].getResource().capacity()) ) {
                        return extraPools[i].getResource();
                    }
                }
                return extraPools[extraBuffers -1].getResource();
            }
        }
    }

    private int getPosition(int position) {
        if (extraBuffers == 0) {
            return position;
        } else {
            if (position < input.capacity()) {
                return position;
            } else {
                int offset = input.capacity();
                for (int i = 0; i < extraPools.length; i++) {
                    if (position < (offset + extraPools[i].getResource().capacity()) ) {
                        return (position - offset);
                    }
                    offset += extraPools[i].getResource().capacity();
                }
                return position;
            }
        }
    }

}