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
import io.undertow.websockets.UTF8StreamSourceChannel;
import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * WebSocket08FrameSinkChannel that is used to write WebSocketFrameType#TEXT frames.
 *
 * It will check if the written payload contain any non-UTF8 data and if so throw
 * an {@link java.io.UnsupportedEncodingException}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket08TextFrameSinkChannel extends WebSocket08FrameSinkChannel {
    private final UTF8Checker checker = new UTF8Checker();

    public WebSocket08TextFrameSinkChannel(StreamSinkChannel channel, WebSocket08Channel wsChannel, long payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.TEXT, payloadSize);
    }

    @Override
    public boolean isFragmentationSupported() {
        return true;
    }

    @Override
    public boolean areExtensionsSupported() {
        return true;
    }

    @Override
    protected int write0(ByteBuffer src) throws IOException {
        checker.checkUTF8(src, src.position(), src.limit());
        return super.write0(src);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        for (int i = offset; i < length; i++) {
            ByteBuffer src = srcs[i];
            checker.checkUTF8(src, src.position(), src.limit());
        }
        return super.write0(srcs, offset, length);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected long write0(ByteBuffer[] srcs) throws IOException {
        for (ByteBuffer src: srcs) {
            checker.checkUTF8(src, src.position(), src.limit());
        }
        return super.write0(srcs);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        return super.transferFrom0(new UTF8FileChannel(src, checker), position, count);
    }

    @Override
    protected long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return super.transferFrom0(new UTF8StreamSourceChannel(source, checker), count, throughBuffer);
    }
}
