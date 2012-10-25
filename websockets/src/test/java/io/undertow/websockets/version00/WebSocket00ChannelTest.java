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

import java.io.IOException;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.utils.TestUtils;

import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V00}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00ChannelTest {

    @Test
    public void testSendBinary() throws IOException {
        checkSend(WebSocketFrameType.BINARY, 10, WebSocket00BinaryFrameSinkChannel.class);
    }

    @Test
    public void testSendText() throws IOException {
        checkSend(WebSocketFrameType.TEXT, 10, WebSocket00TextFrameSinkChannel.class);
    }
    
    @Test
    public void testSendClose() throws IOException {
        checkSend(WebSocketFrameType.CLOSE, 0, WebSocket00CloseFrameSinkChannel.class);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSendCloseWithPayload() throws IOException {
        checkSend(WebSocketFrameType.CLOSE, 10, WebSocket00CloseFrameSinkChannel.class);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSendContinuation() throws IOException {
        checkSend(WebSocketFrameType.CONTINUATION, 10, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendPing() throws IOException {
        checkSend(WebSocketFrameType.PING, 10, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendPong() throws IOException {
        checkSend(WebSocketFrameType.PONG, 10, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void checkSend(WebSocketFrameType type, int size, Class<? extends WebSocket00FrameSinkChannel> clazz) throws IOException {
        ConnectedStreamChannel mockChannel = createMock(ConnectedStreamChannel.class);
        expect(mockChannel.getCloseSetter()).andReturn(new ChannelListener.SimpleSetter()).times(2);
        expect(mockChannel.getReadSetter()).andReturn(new ChannelListener.SimpleSetter());
        expect(mockChannel.getWriteSetter()).andReturn(new ChannelListener.SimpleSetter());
        expect(mockChannel.isOpen()).andReturn(true);
        mockChannel.resumeWrites();
        replay(mockChannel);
       

        WebSocket00Channel wsChannel = new WebSocket00Channel(mockChannel, null, "ws://localhost/ws");
        StreamSinkFrameChannel ch = wsChannel.send(type, size);
        assertTrue(clazz.isInstance(ch));
        assertTrue(ch.isOpen());
        TestUtils.verifyAndReset(mockChannel);

         wsChannel.close();
    }
}
