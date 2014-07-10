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

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class Http2ProtocolUtils {

    public static void putInt(final ByteBuffer buffer, int value) {
        buffer.put((byte) (value >> 24));
        buffer.put((byte) (value >> 16));
        buffer.put((byte) (value >> 8));
        buffer.put((byte) value);
    }

    public static void putInt(final ByteBuffer buffer, int value, int position) {
        buffer.put(position, (byte) (value >> 24));
        buffer.put(position + 1, (byte) (value >> 16));
        buffer.put(position + 2, (byte) (value >> 8));
        buffer.put(position + 3, (byte) value);
    }

    public static int readInt(ByteBuffer buffer) {
        int id = (buffer.get() & 0xFF) << 24;
        id += (buffer.get() & 0xFF) << 16;
        id += (buffer.get() & 0xFF) << 8;
        id += (buffer.get() & 0xFF);
        return id;
    }

    private Http2ProtocolUtils() {

    }
}
