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
package io.undertow.websockets.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link WebSocketVersion#V00} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class AbstractFrameSinkChannel extends StreamSinkFrameChannel {
    public AbstractFrameSinkChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type,
                                    long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
    }

    /**
     * Buffer that holds the frame start
     */
    private ByteBuffer start;

    /**
     * buffer that holds the frame end
     */
    private ByteBuffer end;

    private boolean frameStartWritten = false;

    /**
     * Create the {@link ByteBuffer} that will be written as start of the frame.
     * <p/>
     *
     * @return The {@link ByteBuffer} which will be used to start a frame
     */
    protected abstract ByteBuffer createFrameStart();

    /**
     * Create the {@link ByteBuffer} that marks the end of the frame
     *
     * @return The {@link ByteBuffer} that marks the end of the frame
     */
    protected abstract ByteBuffer createFrameEnd();

    @Override
    public boolean isFragmentationSupported() {
        return false;
    }

    @Override
    public boolean areExtensionsSupported() {
        return false;
    }

    @Override
    protected void close0() throws IOException {

    }

    @Override
    protected int write0(ByteBuffer src) throws IOException {
        if (writeFrameStart()) {
            return channel.write(src);
        }
        return 0;
    }

    /**
     * todo: when we get serious about performance we will need to make sure we use direct buffers
     * and a gathering write for this, so we can write out the whole message with a single write()
     * call
     * @return true if the frame start was written
     * @throws IOException
     */
    private boolean writeFrameStart() throws IOException {
        if (!frameStartWritten) {
            if (start == null) {
                start = createFrameStart();
                start.flip();
            }
            while (start.hasRemaining()) {
                final int result = channel.write(start);
                if (result == -1) {
                    throw WebSocketMessages.MESSAGES.channelClosed();
                } else if (result == 0) {
                    return false;
                }
            }
            frameStartWritten = true;
            start = null;
        }
        return true;
    }

    @Override
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (writeFrameStart()) {
            return channel.write(srcs, offset, length);
        }
        return 0;
    }

    @Override
    protected long write0(ByteBuffer[] srcs) throws IOException {
        if (writeFrameStart()) {
            return channel.write(srcs);
        }
        return 0;
    }

    @Override
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        if (writeFrameStart()) {
            return channel.transferFrom(src, position, count);
        }
        return 0;
    }

    @Override
    protected long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (writeFrameStart()) {
            return channel.transferFrom(source, count, throughBuffer);
        }
        return 0;
    }

    @Override
    protected boolean flush0() throws IOException {
        if (writeFrameStart()) {
            if (getState() == ChannelState.SHUTDOWN) {
                //we know end has not been written yet, or the state would be CLOSED
                if (end == null) {
                    end = createFrameEnd();
                }
                while (end.hasRemaining()) {
                    int b = channel.write(end);
                    if (b == -1) {
                        throw WebSocketMessages.MESSAGES.channelClosed();
                    } else if (b == 0) {
                        return false;
                    }
                }
                return true;
            } else {
                return true;
            }
        }
        return false;
    }
}
