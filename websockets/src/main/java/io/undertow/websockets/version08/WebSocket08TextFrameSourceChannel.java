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
package io.undertow.websockets.version08;

import io.undertow.websockets.UTF8Checker;
import io.undertow.websockets.UTF8FileChannel;
import io.undertow.websockets.UTF8StreamSinkChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketFixedPayloadFrameSourceChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

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

    @Override
    public int read0(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int r = channel.read(dst);

        checker.checkUTF8(dst, pos, r);
        return r;
    }

    @Override
    protected long read0(ByteBuffer[] dsts) throws IOException {
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long r = 0;
        for (int a = offset; a < length; a++) {
            int i = read(dsts[a]);
            if (i < 1) {
                break;
            }
            r += i;
        }
        return r;
    }
}
