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

import java.io.IOException;

import org.junit.Test;
import org.xnio.channels.StreamSinkChannel;

/**
 *  
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
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
    
    
}
