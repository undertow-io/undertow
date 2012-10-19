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

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import io.undertow.websockets.WebSocketUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.xnio.Buffers;
import org.xnio.ChannelListener.Setter;
import org.xnio.BufferAllocator;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

public class WebSocket00TextFrameSourceChannelTest {
    private final static Pool<ByteBuffer> POOL = Buffers.allocatedBufferPool(new BufferAllocator<ByteBuffer>() {

        @Override
        public ByteBuffer allocate(int size) throws IllegalArgumentException {
            return ByteBuffer.allocate(size);
        }
        
    }, 1024);


    private final static byte[] TEXT_BYTES = "Text".getBytes(WebSocketUtils.UTF_8);
    private final static byte[] SOURCE_BYTES = new byte[7];
    static {
        System.arraycopy(TEXT_BYTES, 0, SOURCE_BYTES, 0, TEXT_BYTES.length);
        SOURCE_BYTES[4] = (byte) 0xFF;
        SOURCE_BYTES[5] = (byte) 1;
        SOURCE_BYTES[6] = (byte) 2;
    }


    
    @Test
    public void testReadWithBigBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        expect(mockChannel.getBufferPool()).andReturn(POOL);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00TextFrameSourceChannel channel = new WebSocket00TextFrameSourceChannel(pch, mockChannel);
        ByteBuffer readBuffer = ByteBuffer.allocate(10);

        int read = channel.read(readBuffer);
        assertEquals(4, read);

        readBuffer.flip();

        assertEquals(4, readBuffer.remaining());
        assertArrayEquals(TEXT_BYTES, toBytes(readBuffer));

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[] {(byte)1, (byte)2 }, toBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        verify(mockChannel);
        reset(mockChannel);
    }

    @Test
    public void testReadWithSmallBuffer() throws IOException {
        ByteBuffer complete = ByteBuffer.allocate(TEXT_BYTES.length);
        
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        expect(mockChannel.getBufferPool()).andReturn(POOL);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00TextFrameSourceChannel channel = new WebSocket00TextFrameSourceChannel(pch, mockChannel);
        ByteBuffer readBuffer = ByteBuffer.allocate(2);

        int read = channel.read(readBuffer);
        assertEquals(2, read);
        readBuffer.flip();
        assertEquals(2, readBuffer.remaining());
        complete.put(readBuffer);
        
        read = channel.read(readBuffer);
        assertEquals(0, read);

        readBuffer.clear();

        read = channel.read(readBuffer);

        assertEquals(2, read);
        readBuffer.flip();
        assertEquals(2, readBuffer.remaining());
        complete.put(readBuffer);

        complete.flip();

        assertArrayEquals(TEXT_BYTES, toBytes(complete));
        readBuffer.clear();

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[] {(byte)1, (byte)2 }, toBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        verify(mockChannel);
        reset(mockChannel);
    }

    @Test
    public void testReadWithSmallBuffer2() throws IOException {
        ByteBuffer complete = ByteBuffer.allocate(TEXT_BYTES.length);
        
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        expect(mockChannel.getBufferPool()).andReturn(POOL);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00TextFrameSourceChannel channel = new WebSocket00TextFrameSourceChannel(pch, mockChannel);
        ByteBuffer readBuffer = ByteBuffer.allocate(3);

        int read = channel.read(readBuffer);
        assertEquals(3, read);
        readBuffer.flip();
        assertEquals(3, readBuffer.remaining());
        complete.put(readBuffer);
        
        read = channel.read(readBuffer);
        assertEquals(0, read);

        readBuffer.clear();

        read = channel.read(readBuffer);

        assertEquals(1, read);
        readBuffer.flip();
        assertEquals(1, readBuffer.remaining());
        complete.put(readBuffer);

        complete.flip();

        assertArrayEquals(TEXT_BYTES, toBytes(complete));
        readBuffer.clear();

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[] {(byte)1, (byte)2 }, toBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        verify(mockChannel);
        reset(mockChannel);
    }
    
    private byte[] toBytes(ByteBuffer buffer) {
        byte[] readBytes = new byte[buffer.remaining()];
        System.arraycopy(buffer.array(), buffer.arrayOffset() + buffer.position(), readBytes, 0, readBytes.length);
        return readBytes;
    }

    private final class StreamSourceChannelAdapter implements StreamSourceChannel {
        private final ReadableByteChannel channel;
        private final ChannelListener.SimpleSetter<? extends StreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<StreamSourceChannel>(); 
        private final ChannelListener.SimpleSetter<? extends StreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<StreamSourceChannel>(); 

        public StreamSourceChannelAdapter(ReadableByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return channel.read(dst);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public long read(ByteBuffer[] dst) throws IOException {
            return read(dst, 0, dst.length);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return channel.read(dsts[0]);
        }

        @Override
        public void suspendReads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resumeReads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadResumed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void wakeupReads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdownReads() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void awaitReadable() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
            throw new UnsupportedOperationException();
            
        }

        @Override
        public XnioExecutor getReadThread() {
            throw new UnsupportedOperationException();
        }

        @Override
        public XnioWorker getWorker() {
            throw new UnsupportedOperationException();

        }

        @Override
        public boolean supportsOption(Option<?> option) {
            return false;
        }

        @Override
        public <T> T getOption(Option<T> option) throws IOException {
            return null;
        }

        @Override
        public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
            return null;
        }

        @Override
        public long transferTo(long position, long count, FileChannel target) throws IOException {
            return 0;
        }

        @Override
        public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
            return 0;
        }

        @Override
        public Setter<? extends StreamSourceChannel> getReadSetter() {
            return readSetter;
        }

        @Override
        public Setter<? extends StreamSourceChannel> getCloseSetter() {
            return closeSetter;
        }
        
    }
}
