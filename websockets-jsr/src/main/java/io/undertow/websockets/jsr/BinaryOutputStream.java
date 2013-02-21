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

import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import org.xnio.Pool;
import org.xnio.Pooled;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * {@link OutputStream} implementation which buffers all the data until {@link #close()} is called and then will
 * try to send it in a blocking fashion with the provided {@link FragmentedBinaryFrameSender}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class BinaryOutputStream extends OutputStream {
    private final FragmentedBinaryFrameSender sender;
    private final Pooled<ByteBuffer> pooled;
    private boolean closed;
    BinaryOutputStream(FragmentedBinaryFrameSender sender, Pool<ByteBuffer> pool) {
        this.sender = sender;
        pooled = pool.allocate();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();

        ByteBuffer buffer = pooled.getResource();
        int remaining = buffer.remaining();
        if (remaining >= len) {
            buffer.put(b, off, len);
            send(false, false);
        } else {
            int left = len - remaining;
            while (left > 0) {
                buffer.put(b, off, remaining);
                send(false, false);
                remaining = buffer.remaining();
                left -= remaining;
            }
        }
    }

    private void send(boolean force, boolean last) throws IOException {
        ByteBuffer buffer = pooled.getResource();
        if (force || !buffer.hasRemaining()) {
            buffer.flip();
            if (last) {
                sender.finalFragment();
            }
            sender.sendBinary(buffer);
            buffer.clear();
        }
    }


    @Override
    public void write(int b) throws IOException {
        checkClosed();
        ByteBuffer buffer = pooled.getResource();
        buffer.put((byte) b);
        send(false, false);
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

    private void checkClosed() throws IOException {
        if (closed) {
            throw JsrWebSocketMessages.MESSAGES.sendStreamClosed();
        }
    }
}
