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
package io.undertow.websockets.core;

import io.undertow.UndertowMessages;
import org.xnio.Pool;
import org.xnio.Pooled;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * {@link OutputStream} implementation which buffers all the data until {@link #close()} is called and then will
 * try to send it in a blocking fashion with the provided {@link FragmentedMessageChannel}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class BinaryOutputStream extends OutputStream {
    private final FragmentedMessageChannel sender;
    private final Pooled<ByteBuffer> pooled;
    private boolean closed;

    public BinaryOutputStream(FragmentedMessageChannel sender, Pool<ByteBuffer> pool) {
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
            int left = len;
            do {
                int toWrite = Math.min(remaining, left);
                buffer.put(b, off, toWrite);
                off += toWrite;
                left -= toWrite;
                send(false, false);
                remaining = buffer.remaining();
            } while (left > 0);
        }
    }

    private void send(boolean force, boolean last) throws IOException {
        ByteBuffer buffer = pooled.getResource();
        if (force || !buffer.hasRemaining()) {
            buffer.flip();
            StreamSinkFrameChannel channel = sender.send(buffer.remaining(), last);
            while (buffer.hasRemaining()){
                int res = channel.write(buffer);
                if(res == 0) {
                    channel.awaitWritable();
                }
            }
            channel.shutdownWrites();
            while (!channel.flush()) {
                channel.awaitWritable();
            }
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
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
    }
}
