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
package io.undertow.websockets.protocol.version00;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketUtils;
import io.undertow.websockets.utils.StreamSourceChannelAdapter;
import io.undertow.websockets.utils.TestUtils;
import org.easymock.IAnswer;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket00BinaryFrameSourceChannelTest {

    private final static byte[] TEXT_BYTES = "Text".getBytes(WebSocketUtils.UTF_8);
    private final static byte[] SOURCE_BYTES = new byte[6];

    static {
        System.arraycopy(TEXT_BYTES, 0, SOURCE_BYTES, 0, TEXT_BYTES.length);
        SOURCE_BYTES[4] = (byte) 1;
        SOURCE_BYTES[5] = (byte) 2;
    }


    @Test
    public void testReadWithBigBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00BinaryFrameSourceChannel channel = new WebSocket00BinaryFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel, TEXT_BYTES.length);
        ByteBuffer readBuffer = ByteBuffer.allocate(10);

        int read = channel.read(readBuffer);
        assertEquals(4, read);

        readBuffer.flip();

        assertEquals(4, readBuffer.remaining());
        assertArrayEquals(TEXT_BYTES, TestUtils.readableBytes(readBuffer));

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[]{(byte) 1, (byte) 2}, TestUtils.readableBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);
    }

    @Test
    public void testReadWithSmallBuffer() throws IOException {
        ByteBuffer complete = ByteBuffer.allocate(TEXT_BYTES.length);

        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00BinaryFrameSourceChannel channel = new WebSocket00BinaryFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel, TEXT_BYTES.length);
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

        assertArrayEquals(TEXT_BYTES, TestUtils.readableBytes(complete));
        readBuffer.clear();

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[]{(byte) 1, (byte) 2}, TestUtils.readableBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);

    }

    @Test
    public void testReadWithSmallBuffer2() throws IOException {
        ByteBuffer complete = ByteBuffer.allocate(TEXT_BYTES.length);

        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);
        WebSocket00BinaryFrameSourceChannel channel = new WebSocket00BinaryFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel, TEXT_BYTES.length);
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

        assertArrayEquals(TEXT_BYTES, TestUtils.readableBytes(complete));
        readBuffer.clear();

        read = channel.read(readBuffer);
        assertEquals(-1, read);

        // check that the rest was pushed back to the stream
        readBuffer.clear();
        assertEquals(2, pch.read(readBuffer));
        readBuffer.flip();

        assertArrayEquals(new byte[]{(byte) 1, (byte) 2}, TestUtils.readableBytes(readBuffer));

        assertEquals(-1, pch.read(readBuffer));

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);

    }

    @Test
    public void testTransferTo() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);

        File file = File.createTempFile("undertow-j", ".tmp");
        file.deleteOnExit();

        WebSocket00BinaryFrameSourceChannel channel = new WebSocket00BinaryFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel, TEXT_BYTES.length);
        assertEquals("Should read 4 bytes", 4, channel.transferTo(0, 8, new FileOutputStream(file).getChannel()));

        assertEquals("Should have transfered 4 bytes", 4L, file.length());

        InputStream in = new FileInputStream(file);
        int i = 0;
        int b = -1;
        while ((b = in.read()) != -1) {
            assertEquals(SOURCE_BYTES[i++], b);
        }
        in.close();

        assertEquals(4, i);

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel);

    }

    @Ignore("Find out why this fails. No idea atm...")
    @Test
    public void testTransferToWithBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        StreamSinkChannel mockSink = createMock(StreamSinkChannel.class);
        expect(mockSink.write(anyObject(ByteBuffer.class))).andAnswer(new IAnswer<Integer>() {

            @Override
            public Integer answer() throws Throwable {
                ByteBuffer buf = (ByteBuffer) getCurrentArguments()[0];
                assertEquals(8, buf.capacity());
                assertEquals(1, buf.remaining());
                assertEquals(SOURCE_BYTES[0], buf.get());
                return 1;
            }
        });
        replay(mockSink);

        StreamSourceChannel sch = new StreamSourceChannelAdapter(Channels.newChannel(new ByteArrayInputStream(SOURCE_BYTES)));

        PushBackStreamChannel pch = new PushBackStreamChannel(sch);


        ByteBuffer buffer = ByteBuffer.allocate(8);

        WebSocket00BinaryFrameSourceChannel channel = new WebSocket00BinaryFrameSourceChannel(createMock(WebSocketChannel.StreamSourceChannelControl.class), pch, mockChannel, TEXT_BYTES.length);
        assertEquals(1, channel.transferTo(1L, buffer, mockSink));

        assertFalse(buffer.hasRemaining());

        assertTrue(channel.isFinalFragment());

        TestUtils.verifyAndReset(mockChannel, mockSink);

    }


}
