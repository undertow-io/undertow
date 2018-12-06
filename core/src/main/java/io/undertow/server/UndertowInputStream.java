/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import static io.undertow.util.Bits.allAreClear;
import static io.undertow.util.Bits.anyAreSet;

import java.io.IOException;
import java.io.InputStream;

import io.netty.buffer.ByteBuf;
import io.undertow.UndertowMessages;

/**
 * Input stream that reads from the underlying channel. This stream delays creation
 * of the channel till it is actually used.
 *
 * @author Stuart Douglas
 */
public class UndertowInputStream extends InputStream {

    private final HttpServerExchange exchange;

    /**
     * If this stream is ready for a read
     */
    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_FINISHED = 1 << 1;

    private int state;
    private ByteBuf pooled;

    public UndertowInputStream(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b);
        if (read == -1) {
            return -1;
        }
        return b[0] & 0xff;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        readIntoBuffer();
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }
        ByteBuf buffer = pooled;
        int copied = Math.min(len, buffer.readableBytes());
        buffer.readBytes(b, off, copied);
        if (!buffer.isReadable()) {
            pooled.release();
            pooled = null;
        }
        return copied;
    }

    private void readIntoBuffer() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = exchange.readBlocking();
            if (pooled == null) {
                state |= FLAG_FINISHED;
                pooled = null;
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        return exchange.readBytesAvailable();
    }

    @Override
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            return;
        }
        state |= FLAG_CLOSED;
        try {
            while (allAreClear(state, FLAG_FINISHED)) {
                readIntoBuffer();
                if (pooled != null) {
                    pooled.release();
                    pooled = null;
                }
            }
        } catch (IOException | RuntimeException e) {
            //our exchange is all broken, just end it
            exchange.endExchange();
            throw e;
        } finally {
            if (pooled != null) {
                pooled.release();
                pooled = null;
            }
            state |= FLAG_FINISHED;
        }
    }
}
