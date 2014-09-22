/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.protocols.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.UndertowMessages;

/**
 * Stream sink channel that serves as the basis for channels that do not have the ability
 * to write data.
 * <p/>
 * In particular these are:
 * - PING
 * - GO_AWAY
 *
 * @author Stuart Douglas
 */
abstract class Http2NoDataStreamSinkChannel extends AbstractHttp2StreamSinkChannel {

    protected Http2NoDataStreamSinkChannel(Http2Channel channel) {
        super(channel);
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }
}
