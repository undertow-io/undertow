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
package io.undertow.websockets;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.xnio.Buffers;

/**
 * Utility class which holds general useful utility methods which 
 * can be used within WebSocket implementations.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public final class WebSocketUtils {

    public final static Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Generate the MD5 hash out of the given {@link ByteBuffer}
     *
     */
    public static ByteBuffer md5(ByteBuffer buffer) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(buffer);
            return ByteBuffer.wrap(md.digest());
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            throw new InternalError("MD5 not supported on this platform");
        }
    }

    /**
     * Create a {@link ByteBuffer} which holds the UTF8 encoded bytes for the 
     * given {@link String}.
     * 
     * @param utfString The {@link String} to convert
     * @return buffer   The {@link ByteBuffer} which was created
     */
    public static ByteBuffer fromUtf8String(String utfString) {
        if (utfString == null || utfString.length() == 0) {
            return Buffers.EMPTY_BYTE_BUFFER;
        } else {
            return ByteBuffer.wrap(utfString.getBytes(UTF_8));
        }
    }

    private WebSocketUtils() {
        // utility class
    }
}
