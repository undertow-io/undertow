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
package io.undertow.websockets.utils;

import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.nio.ByteBuffer;

/**
 * An utility class which is used for testing
 *
 * @author norman
 */
public final class TestUtils {

    private TestUtils() {
        // utility class
    }

    /**
     * Return a array of bytes that holds all the readable data of the {@link ByteBuffer}. It will not increase the position
     * of the given {@link ByteBuffer}
     */
    public static byte[] readableBytes(ByteBuffer buffer) {
        byte[] readBytes = new byte[buffer.remaining()];
        System.arraycopy(buffer.array(), buffer.arrayOffset() + buffer.position(), readBytes, 0, readBytes.length);
        return readBytes;
    }

    /**
     * Verify and reset the mocks which were created via {@link org.easymock.EasyMock}.
     */
    public static void verifyAndReset(Object... objects) {
        verify(objects);
        reset(objects);
    }
}
