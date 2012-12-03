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
package io.undertow.websockets.utf8;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.protocol.WebSocketFixedPayloadMaskedFrameSourceChannel;
import io.undertow.websockets.protocol.version07.WebSocket07Channel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class UTF8FixedPayloadMaskedFrameSourceChannel extends WebSocketFixedPayloadMaskedFrameSourceChannel {
    private final UTF8Checker checker;

    protected UTF8FixedPayloadMaskedFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int mask, UTF8Checker checker) {
        super(streamSourceChannelControl, channel, wsChannel, type,  payloadSize, rsv, finalFragment, masked, mask);
        this.checker = checker;
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        if (checker == null) {
            return super.transferTo0(position, count, target);
        }
        return super.transferTo0(position, count, new UTF8FileChannel(target, checker));
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        if (checker == null) {
            return super.read0(dst);
        }
        int r = super.read0(dst);
        checkUTF8(dst);
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

    @Override
    protected void complete() throws IOException {
        if (isFinalFragment()) {
            checker.complete();
        }
        super.complete();
    }

    protected void checkUTF8(ByteBuffer buffer) throws IOException{
        checker.checkUTF8AfterRead(buffer);
    }
}