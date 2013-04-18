/*
 * Copyright 2013 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.jsr;

import io.undertow.websockets.api.FragmentedTextFrameSender;
import org.xnio.Pool;
import org.xnio.Pooled;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * {@link Writer} implementation which buffers all the data until {@link #close()} is called and then will
 * try to send it in a blocking fashion with the provided {@link FragmentedTextFrameSender}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class TextWriter extends Writer {
    private final FragmentedTextFrameSender sender;
    private final Pooled<ByteBuffer> pooled;
    private final CharBuffer buffer;

    private boolean closed;

    public TextWriter(FragmentedTextFrameSender sender, Pool<ByteBuffer> pool) {
        this.sender = sender;
        pooled = pool.allocate();
        buffer = pooled.getResource().asCharBuffer();
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        int remaining = buffer.remaining();
        if (remaining >= len) {
            buffer.put(cbuf, off, len);
            send(false, false);
        } else {
            int left = len;
            do {
                int toWrite = Math.min(remaining, left);
                buffer.put(cbuf, off, toWrite);
                off += toWrite;
                left -= toWrite;
                send(false, false);
                remaining = buffer.remaining();
            } while (left > 0);
        }
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
        send(true, false);

    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                closed = true;
                send(true, true);
            } finally {
                pooled.free();
            }
        }
    }

    private void send(boolean force, boolean last) throws IOException {
        if (force || !buffer.hasRemaining()) {
            buffer.flip();
            if (last) {
                sender.finalFragment();
            }
            sender.sendText(buffer);
            buffer.clear();
        }
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw JsrWebSocketMessages.MESSAGES.sendWriterClosed();
        }
    }
}
