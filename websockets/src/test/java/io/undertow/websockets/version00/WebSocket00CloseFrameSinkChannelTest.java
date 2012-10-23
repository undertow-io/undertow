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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.websockets.utils.TestUtils;

import org.junit.Test;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 *  
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00CloseFrameSinkChannelTest {

    @Test(expected = IOException.class)
    public void testWriteWithBuffer() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        
        StreamSinkChannel mockStreamChannel = createMock(StreamSinkChannel.class);
        expect(mockStreamChannel.isOpen()).andReturn(true);
        replay(mockStreamChannel);
        
        try {
            WebSocket00CloseFrameSinkChannel channel = new WebSocket00CloseFrameSinkChannel(mockStreamChannel, mockChannel);
            channel.write(ByteBuffer.allocate(8));
        } finally {
            TestUtils.verifyAndReset(mockChannel, mockStreamChannel);
        }
    }
    

    @Test(expected = IOException.class)
    public void testWriteWithBuffers() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        
        StreamSinkChannel mockStreamChannel = createMock(StreamSinkChannel.class);
        expect(mockStreamChannel.isOpen()).andReturn(true);
        replay(mockStreamChannel);
        
        try {
            WebSocket00CloseFrameSinkChannel channel = new WebSocket00CloseFrameSinkChannel(mockStreamChannel, mockChannel);
            channel.write(new ByteBuffer[] {ByteBuffer.allocate(8), ByteBuffer.allocate(8)});
        } finally {
            TestUtils.verifyAndReset(mockChannel, mockStreamChannel);
        }
    }
    
    @Test(expected = IOException.class)
    public void testWriteWithBuffersAndOffset() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        
        StreamSinkChannel mockStreamChannel = createMock(StreamSinkChannel.class);
        expect(mockStreamChannel.isOpen()).andReturn(true);
        replay(mockStreamChannel);
        
        try {
            WebSocket00CloseFrameSinkChannel channel = new WebSocket00CloseFrameSinkChannel(mockStreamChannel, mockChannel);
            channel.write(new ByteBuffer[] {ByteBuffer.allocate(8), ByteBuffer.allocate(8)}, 1, 4);
        } finally {
            TestUtils.verifyAndReset(mockChannel, mockStreamChannel);
        }
    }

    @Test(expected = IOException.class)
    public void testTransferFrom() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        
        StreamSinkChannel mockStreamChannel = createMock(StreamSinkChannel.class);
        expect(mockStreamChannel.isOpen()).andReturn(true);
        replay(mockStreamChannel);
        
        FileChannel mockFileChannel = createMock(FileChannel.class);
        replay(mockFileChannel);
        
        try {
            WebSocket00CloseFrameSinkChannel channel = new WebSocket00CloseFrameSinkChannel(mockStreamChannel, mockChannel);
            channel.transferFrom(mockFileChannel, 0, 10);
        } finally {
            TestUtils.verifyAndReset(mockChannel, mockStreamChannel, mockFileChannel);
        }
    }


    @Test(expected = IOException.class)
    public void testTransferFromSource() throws IOException {
        WebSocket00Channel mockChannel = createMock(WebSocket00Channel.class);
        replay(mockChannel);
        
        StreamSinkChannel mockStreamChannel = createMock(StreamSinkChannel.class);
        expect(mockStreamChannel.isOpen()).andReturn(true);
        replay(mockStreamChannel);
        
        StreamSourceChannel mockSourceChannel = createMock(StreamSourceChannel.class);
        replay(mockSourceChannel);
        
        try {
            WebSocket00CloseFrameSinkChannel channel = new WebSocket00CloseFrameSinkChannel(mockStreamChannel, mockChannel);
            channel.transferFrom(mockSourceChannel, 1, ByteBuffer.allocate(8));
        } finally {
            TestUtils.verifyAndReset(mockChannel, mockStreamChannel, mockSourceChannel);
        }
    }
}
