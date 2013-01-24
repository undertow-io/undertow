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
package io.undertow.websockets.impl;

import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.Pooled;
import org.xnio.channels.BlockingWritableByteChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * Wraps a  {@link StreamSinkChannel} and allow to do blocking writes on it via a {@link Writer} abstraction.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class TextWriter extends Writer {
    private final StreamSinkChannel sink;
    private final BlockingWritableByteChannel ch;
    private final WebSocketChannel channel;

    public TextWriter(WebSocketChannel channel, StreamSinkChannel sink) {
        this.sink = sink;
        this.channel = channel;
        ch = new BlockingWritableByteChannel(sink);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        Pooled<ByteBuffer> pooled = channel.getBufferPool().allocate();
        try {
            ByteBuffer buffer = pooled.getResource();

            for (; off < len; off++) {
                buffer.putChar(cbuf[off]);
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                ch.write(buffer);
            }
        } finally {
            pooled.free();
        }

    }

    @Override
    public void flush() {
        // do nothing even if it may be against the contract
        // Need a few more thoughts...
    }

    @Override
    public void close() throws IOException {
        sink.shutdownWrites();
        ch.flush();
        ch.close();
    }
}
