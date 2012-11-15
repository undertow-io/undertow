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
package io.undertow.websockets.protocol.version07;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.protocol.WebSocketFixedPayloadMaskedFrameSourceChannel;
import io.undertow.websockets.utf8.UTF8Checker;
import io.undertow.websockets.utf8.UTF8FileChannel;
import io.undertow.websockets.utf8.UTF8StreamSinkChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07TextFrameSourceChannel extends WebSocketFixedPayloadMaskedFrameSourceChannel {
    private final UTF8Checker checker;

    public WebSocket07TextFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int mask) {
        this(streamSourceChannelControl, channel, wsChannel, payloadSize, rsv, finalFragment, masked, mask, true);
    }

    public WebSocket07TextFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int mask, boolean checkUtf8) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.TEXT, payloadSize, rsv, finalFragment, masked, mask);
        if (checkUtf8) {
            checker = new UTF8Checker();
        } else {
            checker = null;
        }
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        if (checker == null) {
            return super.transferTo0(position, count, target);
        }
        return super.transferTo0(position, count, new UTF8FileChannel(target, checker));
    }

    @Override
    public long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (checker == null) {
            return super.transferTo0(count, throughBuffer, target);
        }
        return super.transferTo0(count, throughBuffer, new UTF8StreamSinkChannel(target, checker));
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        if (checker == null) {
            return super.read0(dst);
        }
        int r = super.read0(dst);
        checker.checkUTF8AfterRead(dst);
        return r;
    }

    @Override
    protected long read0(ByteBuffer[] dsts) throws IOException {
        if (checker == null) {
            return super.read0(dsts);
        }
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (checker == null) {
            return super.read0(dsts, offset, length);
        }
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
