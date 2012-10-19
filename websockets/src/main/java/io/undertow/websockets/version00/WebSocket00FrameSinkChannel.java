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
import io.undertow.websockets.WebSocketVersion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * 
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link WebSocketVersion#V00} connections
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
abstract class WebSocket00FrameSinkChannel  extends StreamSinkFrameChannel {
    public WebSocket00FrameSinkChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type,
            long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
    }

    private final ByteBuffer start = createFrameStart();
    
    private long written = 0;
  
    private boolean frameStartWritten = false;

    /**
     * Create the {@link ByteBuffer} that will be written as start of the frame.
     * 
     * @return startBuffer      The {@link ByteBuffer} which will be used to start a frame
     */
    protected abstract ByteBuffer createFrameStart();
 
    /**
     * Create the {@link ByteBuffer} that marks the end of the frame
     * 
     * @return endBuffer        The {@link ByteBuffer} that marks the end of the frame
     */
    protected abstract ByteBuffer createFrameEnd();

    @Override
    protected boolean close0() throws IOException {
         if (written != payloadSize) {
             try {
                 throw new IOException("Written Payload does not match");
             } finally {
                 channel.close();
             }
         } else {
             final ByteBuffer buf = createFrameEnd();
 
             while(buf.hasRemaining()) {
                 if (channel.write(buf) == 0) {
                     channel.getWriteSetter().set(new CloseListener(buf));
                     channel.resumeWrites();
                     return false;
                 }
             }
             channel.getWriteSetter().set(null);
             channel.suspendWrites();
            return true;
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
            while(start.hasRemaining()) {
                if (channel.write(start) < 1) {
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

    private final class CloseListener implements ChannelListener<StreamSinkChannel> {

        private final ByteBuffer buf;

        CloseListener(ByteBuffer buf) {
            this.buf = buf;
        }

        @Override
        public void handleEvent(StreamSinkChannel channel) {
            try {
                while (buf.hasRemaining()) {
                    int w = channel.write(buf);
                    if (w == 0) {
                        // not everything written
                        if(!channel.isWriteResumed()) {
                            Channels.setWriteListener(channel, this);
                            channel.resumeWrites();
                        }
                        return;
                    }
                    if (w == -1) {
                        channel.suspendWrites();
                        
                        // TODO: IS this correct?
                        channel.close();
                        return;
                    }
                }
                if (!channel.flush()) {
                    // TODO: Is this needed ?
                    channel.shutdownWrites();
                    
                    // flush did not work out, so set a new listener that will try to flush again
                    channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<StreamSinkChannel>() {

                        @Override
                        public void handleEvent(StreamSinkChannel channel) {
                            try {
                                // flushed now remove the channel
                                WebSocket00FrameSinkChannel.this.recycle();
                            } catch (IOException e) {
                                // TODO: Logging
                                IoUtils.safeClose(channel);
                            }
                        }
                        
                    }, ChannelListeners.closingChannelExceptionHandler()));
                    // Make sure we get notified again when write was possible
                    channel.resumeWrites();

                } else {
                    // everything flushed out now its safe to remove this channel
                    WebSocket00FrameSinkChannel.this.recycle();
                }
            } catch (IOException e) {
                // TODO: Logging
                IoUtils.safeClose(channel);
            }
        }
        
    }
}
