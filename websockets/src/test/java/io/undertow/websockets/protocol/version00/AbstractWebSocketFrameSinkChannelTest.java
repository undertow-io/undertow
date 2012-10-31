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

import io.undertow.websockets.WebSocketUtils;



/**
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class AbstractWebSocketFrameSinkChannelTest {
    protected final static byte[] DATA = "MyData".getBytes(WebSocketUtils.UTF_8);
/*
    @Test
    public void testWriteWithBuffer() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            byte[] start = TestUtils.readableBytes((ByteBuffer) channel.createFrameStart().flip());
            byte[] end = TestUtils.readableBytes((ByteBuffer) channel.createFrameEnd().flip());

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            int written = 0;

            while(buf.hasRemaining()) {
                written += channel.write(buf);
            }
            assertEquals(DATA.length, written);

            channel.close();

            checkWrittenData(start, DATA, end, out.toByteArray());

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testWriteWithBufferNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.wrap(DATA);
            assertEquals(0, channel.write(buf));

            try {
                channel.close();
                fail();
            } catch (IOException e) {
                // expected
            }

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test(expected = IOException.class)
    public void testWriteWithBufferWithCorruptedPayload() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.wrap(DATA);
            buf = (ByteBuffer) buf.limit(buf.limit() -1);
            int written = 0;

            while(buf.hasRemaining()) {
                written += channel.write(buf);
            }
            assertEquals(DATA.length - 1, written);

            channel.close();

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testWriteWithBuffers() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            byte[] start = TestUtils.readableBytes((ByteBuffer) channel.createFrameStart().flip());
            byte[] end = TestUtils.readableBytes((ByteBuffer) channel.createFrameEnd().flip());

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            int written = 0;

            while(bufs[0].hasRemaining() || bufs[1].hasRemaining()) {
                written += channel.write(bufs);
            }
            assertEquals(DATA.length, written);

            channel.close();

            checkWrittenData(start, DATA, end, out.toByteArray());

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testWriteWithBuffersNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            assertEquals(0, channel.write(bufs));

            try {
                channel.close();
                fail();
            } catch (IOException e) {
                // expected
            }

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test(expected = IOException.class)
    public void testWriteWithBuffersWithCorruptedPayload() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit() - 1);

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            int written = 0;

            while(bufs[0].hasRemaining() || bufs[1].hasRemaining()) {
                written += channel.write(bufs);
            }
            assertEquals(DATA.length - 1, written);

            channel.close();
        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testWriteWithBuffersWithOffset() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            byte[] start = TestUtils.readableBytes((ByteBuffer) channel.createFrameStart().flip());
            byte[] end = TestUtils.readableBytes((ByteBuffer) channel.createFrameEnd().flip());

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            int written = 0;


            while(bufs[0].hasRemaining() || bufs[1].hasRemaining()) {
                written += channel.write(bufs, 0, 2);
            }
            assertEquals(DATA.length, written);

            channel.close();
            byte[] payload = new byte[DATA.length -2];
            System.arraycopy(DATA, 2, payload, 0, payload.length);
            checkWrittenData(start, payload, end, out.toByteArray());

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testWriteWithBuffersWithOffsetNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};

            assertEquals(0, channel.write(bufs, 0, 2));

            try {
                channel.close();
                fail();
            } catch (IOException e) {
                // expected
            }

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test(expected = IOException.class)
    public void testWriteWithBuffersWithOffsetWithCorruptPayload() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            int written = 0;


            while(bufs[1].hasRemaining()) {
                written += channel.write(bufs, 1, 2);
            }
            assertEquals(DATA.length - 2, written);

            channel.close();

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    private static void checkWrittenData(byte[] start, byte[] payload, byte[] end, byte[] writtenData) {
        int i = 0;
        for (; i < writtenData.length; i++) {
            if (i < start.length) {
                assertEquals(start[i], writtenData[i]);
            } else {
                int a = i - start.length;
                if (a < DATA.length) {
                    assertEquals(DATA[a], writtenData[i]);
                } else {
                    a -= DATA.length;

                    assertEquals(end[a], writtenData[i]);

                }
            }
        }
        assertEquals(writtenData.length, i);
    }


    @Test
    public void testTransferFrom() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileChannel fchannel = new FileInputStream(file).getChannel();
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            byte[] start = TestUtils.readableBytes((ByteBuffer) channel.createFrameStart().flip());
            byte[] end = TestUtils.readableBytes((ByteBuffer) channel.createFrameEnd().flip());

            long written = 0;

            while(written < DATA.length) {
                written += channel.transferFrom(fchannel, written, DATA.length - written);
            }
            assertEquals(DATA.length, written);

            channel.close();
            checkWrittenData(start, DATA, end, out.toByteArray());

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testTransferFromNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileChannel fchannel = new FileInputStream(file).getChannel();
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            assertEquals(0, channel.transferFrom(fchannel, 0, DATA.length));

            try {
                channel.close();
                fail();
            } catch (IOException e) {
                // expected
            }
        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }


    @Test(expected = IOException.class)
    public void testTransferFromWithCorruptedPayload() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileChannel fchannel = new FileInputStream(file).getChannel();
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);

            long written = 0;

            while(written < DATA.length -1 ) {
                written += channel.transferFrom(fchannel, written, DATA.length - written -1);
            }
            assertEquals(DATA.length - 1, written);

            channel.close();
        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testTransferFromSource() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamSourceChannelAdapter fchannel = new StreamSourceChannelAdapter(Channels.newChannel(new FileInputStream(file)));
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            byte[] start = TestUtils.readableBytes((ByteBuffer) channel.createFrameStart().flip());
            byte[] end = TestUtils.readableBytes((ByteBuffer) channel.createFrameEnd().flip());

            long written = 0;

            ByteBuffer buf = ByteBuffer.allocate(8);

            while(written < DATA.length) {
                written += channel.transferFrom(fchannel, DATA.length - written, (ByteBuffer) buf.clear());
            }
            assertEquals(DATA.length, written);

            channel.close();
            checkWrittenData(start, DATA, end, out.toByteArray());

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test
    public void testTransferFromSourceNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamSourceChannelAdapter fchannel = new StreamSourceChannelAdapter(Channels.newChannel(new FileInputStream(file)));
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.allocate(8);
            assertEquals(0, channel.transferFrom(fchannel, DATA.length, (ByteBuffer) buf.clear()));

            try {
                channel.close();
                fail();
            } catch (IOException e) {
                // expected
            }

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    @Test(expected = IOException.class)
    public void testTransferFromSourceWithCorruptPayload() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();

        replay(mockChannel);

        File file = File.createTempFile("undertow-test", ".tmp");
        file.deleteOnExit();
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(DATA);
        fout.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamSourceChannelAdapter fchannel = new StreamSourceChannelAdapter(Channels.newChannel(new FileInputStream(file)));
        WebSocket00Channel wsChannel = createWSChannel(mockChannel, true);
        try {
            AbstractFrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length + 1);

            long written = 0;

            ByteBuffer buf = ByteBuffer.allocate(8);

            while(written < DATA.length) {
                written += channel.transferFrom(fchannel, DATA.length - written, (ByteBuffer) buf.clear());
            }
            assertEquals(DATA.length, written);

            channel.close();
        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }

    protected static WebSocket00Channel createWSChannel(ConnectedStreamChannel channel, final boolean inUse) {
        return new WebSocket00Channel(channel, null, "ws://localhost/ws") {

            @Override
            protected boolean isActive(StreamSinkChannel channel) {
                return inUse;
            }

        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected static ConnectedStreamChannel createMockChannel() {
        ConnectedStreamChannel mockChannel = createMock(ConnectedStreamChannel.class);
        expect(mockChannel.getCloseSetter()).andReturn(new ChannelListener.SimpleSetter()).times(2);
        expect(mockChannel.getReadSetter()).andReturn(new ChannelListener.SimpleSetter());
        expect(mockChannel.getWriteSetter()).andReturn(new ChannelListener.SimpleSetter());
        return mockChannel;
    }

    protected abstract AbstractFrameSinkChannel createChannel(StreamSinkChannel channel, WebSocket00Channel wsChannel, int payloadLength);

    */
}
