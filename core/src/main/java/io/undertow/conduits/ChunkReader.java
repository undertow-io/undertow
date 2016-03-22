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

import io.undertow.UndertowMessages;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.conduits.Conduit;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Utility class for reading chunked streams.
 *
 * @author Stuart Douglas
 */
class ChunkReader<T extends Conduit> {

    private static final long FLAG_FINISHED = 1L << 62L;
    private static final long FLAG_READING_LENGTH = 1L << 61L;
    private static final long FLAG_READING_TILL_END_OF_LINE = 1L << 60L;
    private static final long FLAG_READING_NEWLINE = 1L << 59L;
    private static final long FLAG_READING_AFTER_LAST = 1L << 58L;

    private static final long MASK_COUNT = longBitMask(0, 56);

    private long state;
    private final Attachable attachable;
    private final AttachmentKey<HeaderMap> trailerAttachmentKey;
    /**
     * The trailer parser that stores the trailer parse state. If this class is not null it means
     * that we are in the middle of parsing trailers.
     */
    private TrailerParser trailerParser;

    private final ConduitListener<? super T> finishListener;
    private final T conduit;

    ChunkReader(final Attachable attachable, final AttachmentKey<HeaderMap> trailerAttachmentKey, ConduitListener<? super T> finishListener, T conduit) {
        this.attachable = attachable;
        this.trailerAttachmentKey = trailerAttachmentKey;
        this.finishListener = finishListener;
        this.conduit = conduit;
        this.state = FLAG_READING_LENGTH;
    }

    public long readChunk(final ByteBuffer buf) throws IOException {
        long oldVal = state;
        long chunkRemaining = state & MASK_COUNT;

        if (chunkRemaining > 0 && !anyAreSet(state, FLAG_READING_AFTER_LAST | FLAG_READING_LENGTH | FLAG_READING_NEWLINE | FLAG_READING_TILL_END_OF_LINE)) {
            return chunkRemaining;
        }
        long newVal = oldVal & ~MASK_COUNT;
        try {
            if (anyAreSet(oldVal, FLAG_READING_AFTER_LAST)) {
                int ret = handleChunkedRequestEnd(buf);
                if (ret == -1) {
                    newVal |= FLAG_FINISHED & ~FLAG_READING_AFTER_LAST;
                    return -1;
                }
                return 0;
            }

            while (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                while (buf.hasRemaining()) {
                    byte b = buf.get();
                    if (b == '\n') {
                        newVal = newVal & ~FLAG_READING_NEWLINE | FLAG_READING_LENGTH;
                        break;
                    }
                }
                if (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                    return 0;
                }
            }

            while (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                while (buf.hasRemaining()) {
                    byte b = buf.get();
                    if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F')) {
                        chunkRemaining <<= 4; //shift it 4 bytes and then add the next value to the end
                        chunkRemaining += Character.digit((char) b, 16);
                    } else {
                        if (b == '\n') {
                            newVal = newVal & ~FLAG_READING_LENGTH;
                        } else {
                            newVal = newVal & ~FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE;
                        }
                        break;
                    }
                }
                if (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                    return 0;
                }
            }
            while (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                while (buf.hasRemaining()) {
                    if (buf.get() == '\n') {
                        newVal = newVal & ~FLAG_READING_TILL_END_OF_LINE;
                        break;
                    }
                }
                if (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                    return 0;
                }
            }

            //we have our chunk size, check to make sure it was not the last chunk
            if (allAreClear(newVal, FLAG_READING_NEWLINE | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
                newVal |= FLAG_READING_AFTER_LAST;
                int ret = handleChunkedRequestEnd(buf);
                if (ret == -1) {
                    newVal |= FLAG_FINISHED & ~FLAG_READING_AFTER_LAST;
                    return -1;
                }
                return 0;
            }
            return chunkRemaining;
        } finally {
            state = newVal | chunkRemaining;

            if (allAreClear(oldVal, FLAG_FINISHED) && allAreSet(newVal, FLAG_FINISHED)) {
                if (finishListener != null) {
                    finishListener.handleEvent(conduit);
                }
            }
        }
    }

    public long getChunkRemaining() {
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if(anyAreSet(state, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_READING_AFTER_LAST)) {
            return 0;
        }
        return state & MASK_COUNT;
    }

    public void setChunkRemaining(final long remaining) {
        if (remaining < 0  || anyAreSet(state, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_READING_AFTER_LAST)) {
            return;
        }
        long old = state;
        long oldRemaining = old & MASK_COUNT;
        if (remaining == 0 && oldRemaining != 0) {
            //if oldRemaining is zero it could be that no data has been read yet
            //and the correct state is READING_LENGTH
            old |= FLAG_READING_NEWLINE;
        }
        state = (old & ~MASK_COUNT) | remaining;
    }

    private int handleChunkedRequestEnd(ByteBuffer buffer) throws IOException {
        if (trailerParser != null) {
            return trailerParser.handle(buffer);
        }
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\n') {
                return -1;
            } else if (b != '\r') {
                buffer.position(buffer.position() - 1);
                trailerParser = new TrailerParser();
                return trailerParser.handle(buffer);
            }
        }
        return 0;
    }

    /**
     * Class that parses HTTP trailers. We don't just re-use the http parser code because it is complicated enough
     * already, and this is not used very often so the performance benefits should not matter.
     */
    private final class TrailerParser {

        private HeaderMap headerMap = new HeaderMap();
        private StringBuilder builder = new StringBuilder();
        private HttpString httpString;
        int state = 0;

        private static final int STATE_TRAILER_NAME = 0;
        private static final int STATE_TRAILER_VALUE = 1;
        private static final int STATE_ENDING = 2;


        public int handle(ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) {
                final byte b = buf.get();
                if (state == STATE_TRAILER_NAME) {
                    if (b == '\r') {
                        if (builder.length() == 0) {
                            state = STATE_ENDING;
                        } else {
                            throw UndertowMessages.MESSAGES.couldNotDecodeTrailers();
                        }
                    } else if (b == '\n') {
                        if (builder.length() == 0) {
                            attachable.putAttachment(trailerAttachmentKey, headerMap);
                            return -1;
                        } else {
                            throw UndertowMessages.MESSAGES.couldNotDecodeTrailers();
                        }
                    } else if (b == ':') {
                        httpString = HttpString.tryFromString(builder.toString().trim());
                        state = STATE_TRAILER_VALUE;
                        builder.setLength(0);
                    } else {
                        builder.append((char) b);
                    }
                } else if (state == STATE_TRAILER_VALUE) {
                    if (b == '\n') {
                        headerMap.put(httpString, builder.toString().trim());
                        httpString = null;
                        builder.setLength(0);
                        state = STATE_TRAILER_NAME;
                    } else if (b != '\r') {
                        builder.append((char) b);
                    }
                } else if (state == STATE_ENDING) {
                    if (b == '\n') {
                        if (attachable != null) {
                            attachable.putAttachment(trailerAttachmentKey, headerMap);
                        }
                        return -1;
                    } else {
                        throw UndertowMessages.MESSAGES.couldNotDecodeTrailers();
                    }
                } else {
                    throw new IllegalStateException();
                }
            }
            return 0;
        }
    }

}
