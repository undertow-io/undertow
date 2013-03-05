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
package io.undertow.websockets.core.protocol.version00;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.utils.StreamSourceChannelAdapter;
import io.undertow.websockets.utils.TestUtils;
import org.junit.Test;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00CloseFrameSourceChannelTest {

    @Test
    public void testReadWithByteBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(new byte[] { (byte) 1, (byte) 2})));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);

        WebSocket00CloseFrameSourceChannel channel = new WebSocket00CloseFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel);
        ByteBuffer buffer = ByteBuffer.allocate(8);
        assertEquals(-1, channel.read(buffer));

        assertEquals("Nothing should be read", buffer.capacity(), buffer.remaining());

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }


    @Test
    public void testReadWithByteBuffers() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(new byte[] { (byte) 1, (byte) 2})));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);

        WebSocket00CloseFrameSourceChannel channel = new WebSocket00CloseFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class),pch, mockChannel);
        ByteBuffer[] buffers = {ByteBuffer.allocate(8), ByteBuffer.allocate(8)};
        assertEquals(-1, channel.read(buffers));

        for (ByteBuffer buffer: buffers) {
            assertEquals("Nothing should be read", buffer.capacity(), buffer.remaining());
        }

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }


    @Test
    public void testReadWithByteBuffersWithOffset() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(new byte[] { (byte) 1, (byte) 2})));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);

        WebSocket00CloseFrameSourceChannel channel = new WebSocket00CloseFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class),pch, mockChannel);
        ByteBuffer[] buffers = {ByteBuffer.allocate(8), ByteBuffer.allocate(8)};
        assertEquals(-1, channel.read(buffers, 0 , 1));

        for (ByteBuffer buffer: buffers) {
            assertEquals("Nothing should be read", buffer.capacity(), buffer.remaining());
        }

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }

    @Test
    public void testTransferTo() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(
                new byte[] { (byte) 1, (byte) 2 })));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);

        File file = File.createTempFile("undertow", ".tmp");
        file.deleteOnExit();

        WebSocket00CloseFrameSourceChannel channel = new WebSocket00CloseFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class),pch, mockChannel);
        assertEquals(-1, channel.transferTo(0, 8, new FileOutputStream(file).getChannel()));

        assertEquals("Nothing should be read", 0L, file.length());

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }

    @Test
    public void testTransferToWithBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        StreamSinkChannel mockSink = createMock(StreamSinkChannel.class);
        replay(mockSink);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(
                new byte[] { (byte) 1, (byte) 2 })));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);


        ByteBuffer buffer = ByteBuffer.allocate(8);

        WebSocket00CloseFrameSourceChannel channel = new WebSocket00CloseFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class),pch, mockChannel);
        assertEquals(-1, channel.transferTo(1L, buffer, mockSink));

        assertEquals("Nothing should be read", buffer.capacity(), buffer.remaining());

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }

}
