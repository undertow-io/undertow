
/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.websockets.version00;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
/**
 * 
 * {@link StreamSinkFrameChannel} implementation for writing {@link WebSocketFrameType#BINARY}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00BinaryFrameChannel extends StreamSinkFrameChannel {
    private SimpleSetter<WebSocket00BinaryFrameChannel> setter = new SimpleSetter<WebSocket00BinaryFrameChannel>();
    private long written = 0;
    private final ByteBuffer frameStart = createFrameStart();

    private boolean frameStartWritten = false;
    
    public WebSocket00BinaryFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
    }

    @Override
    public Setter<? extends StreamSinkChannel> getWriteSetter() {
        return setter;
    }

    private ByteBuffer createFrameStart() {
        int dataLen = (int) payloadSize;
        ByteBuffer buffer = ByteBuffer.allocate(5);
        // Encode type.
        buffer.put((byte) 0x80);

        // Encode length.
        int b1 = dataLen >>> 28 & 0x7F;
        int b2 = dataLen >>> 14 & 0x7F;
        int b3 = dataLen >>> 7 & 0x7F;
        int b4 = dataLen & 0x7F;
        if (b1 == 0) {
            if (b2 == 0) {
                if (b3 == 0) {
                    buffer.put((byte) b4);
                } else {
                    buffer.put((byte) (b3 | 0x80));
                    buffer.put((byte)b4);
                }
            } else {
                buffer.put((byte)(b2 | 0x80));
                buffer.put((byte)(b3 | 0x80));
                buffer.put((byte)b4);
            }
        } else {
            buffer.put((byte)(b1 | 0x80));
            buffer.put((byte)(b2 | 0x80));
            buffer.put((byte)(b3 | 0x80));
            buffer.put((byte)b4);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    protected void close0() throws IOException {
         if (written != payloadSize) {
             try {
                 throw new IOException("Written Payload does not match");
             } finally {
                 channel.close();
             }
         }
    }

    @Override
    protected int write0(ByteBuffer src) throws IOException {
        if (writeFrameStart()) {
            int b = channel.write(src);
            written =+ b;
            return b;
        }
        return 0;
    }
    
    private boolean writeFrameStart() throws IOException {
        if (!frameStartWritten) {
            while(frameStart.hasRemaining()) {
                if (channel.write(frameStart) < 1) {
                    return false;
                }
            }
            frameStartWritten = true;
        }
        return true;
    }

    @Override
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (writeFrameStart()) {
            long b = channel.write(srcs, offset, length);
            written =+ b;
            return b;
        }
        return 0;
    }

    @Override
    protected long write0(ByteBuffer[] srcs) throws IOException {
        if (writeFrameStart()) {
            long b = channel.write(srcs);
            written =+ b;
            return b;
        }
        return 0;
    }

    @Override
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        if (writeFrameStart()) {
            long b = channel.transferFrom(src, position, count);
            written =+ b;
            return b;
        }
        return 0;
    }

    @Override
    protected long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (writeFrameStart()) {
            long b = channel.transferFrom(source, count, throughBuffer);
            written =+ b;
            return b;
        }
        return 0;
    }
    

}
