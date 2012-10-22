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
package io.undertow.websockets.utils;

import java.nio.ByteBuffer;

public final class BufferUtils {

    private BufferUtils() {
        // utility class
    }


    public static byte[] readableBytes(ByteBuffer buffer) {
        byte[] readBytes = new byte[buffer.remaining()];
        System.arraycopy(buffer.array(), buffer.arrayOffset() + buffer.position(), readBytes, 0, readBytes.length);
        return readBytes;
    }
}
