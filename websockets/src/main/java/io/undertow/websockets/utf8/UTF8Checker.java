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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import io.undertow.websockets.ChannelFunction;
import io.undertow.websockets.WebSocketMessages;

/**
 * An utility class which can be used to check if a sequence of bytes or ByteBuffers contain non UTF-8 data.
 * <p/>
 * Please use a new instance per stream.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UTF8Checker implements ChannelFunction {


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
    private int codep;

    private void checkUTF8(int b) throws UnsupportedEncodingException {
        byte type = TYPES[b & 0xFF];

        codep = state != UTF8_ACCEPT ? b & 0x3f | codep << 6 : 0xff >> type & b;

        state = STATES[state + type];

        if (state == UTF8_REJECT) {
            throw WebSocketMessages.MESSAGES.invalidTextFrameEncoding();
        }
    }

    /**
     * Check if the given ByteBuffer contains non UTF-8 data.
     *
     * @param buf                               the ByteBuffer to check
     * @param flip                              if the ByteBuffer should be flipped
     * @throws UnsupportedEncodingException     is thrown if non UTF-8 data is found
     */
    private void checkUTF8(ByteBuffer buf, boolean flip) throws UnsupportedEncodingException {
        ByteBuffer b;
        if (flip) {
            b = buf.duplicate();
            b.flip();
        } else {
            b = buf;
        }
        for (int i = b.position(); i < b.limit(); i++) {
            checkUTF8(b.get(i));
        }
    }

    @Override
    public void afterRead(ByteBuffer buf) throws UnsupportedEncodingException{
        checkUTF8(buf, true);
    }

    @Override
    public void beforeWrite(ByteBuffer buf) throws UnsupportedEncodingException{
        checkUTF8(buf, false);
    }

    @Override
    public void complete() throws UnsupportedEncodingException {
        if (state != UTF8_ACCEPT) {
            throw WebSocketMessages.MESSAGES.invalidTextFrameEncoding();
        }
    }
}
