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
package io.undertow.websockets.core.protocol.version07;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.function.ChannelFunction;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * An utility class which can be used to check if a sequence of bytes or ByteBuffers contain non UTF-8 data.
 * <p>
 * Please use a new instance per stream.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class UTF8Checker implements ChannelFunction {


    private static final int UTF8_ACCEPT = 0;
    private static final int UTF8_REJECT = 12;

    private static final byte[] TYPES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
            8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
            8, 8, 8, 8, 8, 8};

    private static final byte[] STATES = {0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
            12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
            12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12};

    private int state = UTF8_ACCEPT;

    private void checkUTF8(int b) throws UnsupportedEncodingException {
        byte type = TYPES[b & 0xFF];

        state = STATES[state + type];
        if (state == UTF8_REJECT) {
            throw WebSocketMessages.MESSAGES.invalidTextFrameEncoding();
        }
    }

    /**
     * Check if the given ByteBuffer contains non UTF-8 data.
     *
     * @param buf           the ByteBuffer to check
     * @param position      the index in the {@link ByteBuffer} to start from
     * @param length        the number of bytes to operate on
     * @throws UnsupportedEncodingException     is thrown if non UTF-8 data is found
     */
    private void checkUTF8(ByteBuffer buf, int position, int length) throws UnsupportedEncodingException {
        int limit = position + length;
        for (int i = position; i < limit; i++) {
            checkUTF8(buf.get(i));
        }
    }

    @Override
    public void newFrame(FrameHeaderData headerData) {
    }

    @Override
    public void afterRead(ByteBuffer buf, int position, int length) throws IOException{
        checkUTF8(buf, position, length);
    }

    @Override
    public void beforeWrite(ByteBuffer buf, int position, int length) throws UnsupportedEncodingException{
        checkUTF8(buf, position, length);
    }

    @Override
    public void complete() throws UnsupportedEncodingException {
        if (state != UTF8_ACCEPT) {
            throw WebSocketMessages.MESSAGES.invalidTextFrameEncoding();
        }
    }
}
