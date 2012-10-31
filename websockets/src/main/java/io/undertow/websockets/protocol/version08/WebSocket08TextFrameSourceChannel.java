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
package io.undertow.websockets.protocol.version08;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.websockets.utf8.UTF8Checker;
import io.undertow.websockets.utf8.UTF8FileChannel;
import io.undertow.websockets.utf8.UTF8StreamSinkChannel;
import io.undertow.websockets.WebSocketFixedPayloadFrameSourceChannel;
import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket08TextFrameSourceChannel extends WebSocketFixedPayloadFrameSourceChannel {
    private final UTF8Checker checker = new UTF8Checker();

    public WebSocket08TextFrameSourceChannel(WebSocket08Channel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket08Channel wsChannel, int rsv, boolean finalFragment, long payloadSize) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.TEXT, rsv, finalFragment, payloadSize);
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        return super.transferTo0(position, count, new UTF8FileChannel(target, checker));
    }

    @Override
    public long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return super.transferTo0(count, throughBuffer, new UTF8StreamSinkChannel(target, checker));
    }

}
