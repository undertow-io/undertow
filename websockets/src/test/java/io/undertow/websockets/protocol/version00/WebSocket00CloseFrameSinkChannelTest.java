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

import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import io.undertow.websockets.utils.StreamSinkChannelAdapter;
import io.undertow.websockets.utils.StreamSourceChannelAdapter;
import io.undertow.websockets.utils.TestUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import org.junit.Ignore;
import org.junit.Test;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;

/**
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
@Ignore
public class WebSocket00CloseFrameSinkChannelTest extends AbstractWebSocketFrameSinkChannelTest {

    @Override
    protected WebSocket00FrameSinkChannel createChannel(StreamSinkChannel channel, WebSocket00Channel wsChannel,
            int payloadLength) {
        return new WebSocket00CloseFrameSinkChannel(channel, wsChannel);
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBuffer() throws IOException {
        super.testWriteWithBuffer();
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBufferWithCorruptedPayload() throws IOException {
        super.testWriteWithBufferWithCorruptedPayload();
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBuffers() throws IOException {
        super.testWriteWithBuffers();
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBuffersWithCorruptedPayload() throws IOException {
        super.testWriteWithBuffersWithCorruptedPayload();
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBuffersWithOffset() throws IOException {
        super.testWriteWithBuffersWithOffset();
    }

    @Override
    @Test(expected = IOException.class)
    public void testWriteWithBuffersWithOffsetWithCorruptPayload() throws IOException {
        super.testWriteWithBuffersWithOffsetWithCorruptPayload();
    }

    @Override
    @Test(expected = IOException.class)
    public void testTransferFrom() throws IOException {
        super.testTransferFrom();
    }

    @Override
    @Test(expected = IOException.class)
    public void testTransferFromWithCorruptedPayload() throws IOException {
        super.testTransferFromWithCorruptedPayload();
    }

    @Override
    @Test(expected = IOException.class)
    public void testTransferFromSource() throws IOException {
        super.testTransferFromSource();
    }

    @Override
    @Test(expected = IOException.class)
    public void testTransferFromSourceWithCorruptPayload() throws IOException {
        super.testTransferFromSourceWithCorruptPayload();
    }

    @Test
    public void testWriteWithBufferNotInUse() throws IOException {
        ConnectedStreamChannel mockChannel = createMockChannel();
        replay(mockChannel);

        WebSocket00Channel wsChannel = createWSChannel(mockChannel, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            WebSocket00FrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.wrap(DATA);
            assertEquals(0, channel.write(buf));

            channel.close();


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
            WebSocket00FrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};
            assertEquals(0, channel.write(bufs));

            channel.close();
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
            WebSocket00FrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);

            ByteBuffer buf = ByteBuffer.wrap(DATA);
            ByteBuffer buf1 = (ByteBuffer) buf.duplicate().limit(2);
            ByteBuffer buf2 = (ByteBuffer) buf.duplicate().position(2).limit(buf.limit());

            ByteBuffer[] bufs = new ByteBuffer[] {buf1, buf2};

            assertEquals(0, channel.write(bufs, 0, 2));

            channel.close();
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
            WebSocket00FrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            assertEquals(0, channel.transferFrom(fchannel, 0, DATA.length));

            channel.close();

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
            WebSocket00FrameSinkChannel channel = createChannel(new StreamSinkChannelAdapter(Channels.newChannel(out)), wsChannel, DATA.length);
            ByteBuffer buf = ByteBuffer.allocate(8);
            assertEquals(0, channel.transferFrom(fchannel, DATA.length, (ByteBuffer) buf.clear()));

            channel.close();

        } finally {
            TestUtils.verifyAndReset(mockChannel);
        }
    }
}
