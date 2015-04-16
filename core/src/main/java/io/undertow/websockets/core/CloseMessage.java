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

package io.undertow.websockets.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A close message
 *
 * @author Stuart Douglas
 */
public class CloseMessage {

    private final int code;
    private final String reason;
 /*
    * For the exact meaning of the codes refer to the <a href="http://tools.ietf.org/html/rfc6455#section-7.4">WebSocket
    * RFC Section 7.4</a>.
    */
  public static final int NORMAL_CLOSURE = 1000;
   public static final int GOING_AWAY = 1001;
   public static final int WRONG_CODE = 1002;
   public static final int PROTOCOL_ERROR = 1003;
   public static final int MSG_CONTAINS_INVALID_DATA = 1007;
   public static final int MSG_VIOLATES_POLICY = 1008;
   public static final int MSG_TOO_BIG = 1009;
   public static final int MISSING_EXTENSIONS = 1010;
   public static final int UNEXPECTED_ERROR = 1011;

    public CloseMessage(final ByteBuffer buffer) {
        if(buffer.remaining() >= 2) {
            code = (buffer.get() & 0XFF) << 8 | (buffer.get() & 0xFF);
            reason = new UTF8Output(buffer).extract();
        } else {
            code = NORMAL_CLOSURE;
            reason = "";
        }
    }

    public CloseMessage(int code, String reason) {
        this.code = code;
        this.reason = reason == null ? "" : reason;
    }

    public CloseMessage(final ByteBuffer[] buffers) {
        this(WebSockets.mergeBuffers(buffers));
    }

    public String getReason() {
        return reason;
    }

    public int getCode() {
        return code;
    }

    public ByteBuffer toByteBuffer() {
        byte[] data = reason.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 2);
        buffer.putShort((short) code);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    /**
     * Return {@code true} if the provided code is a valid close status code.
     */
    public static boolean isValid(int code) {
        if (code >= 0 && code <= 999 || code >= 1004 && code <= 1006
                || code >= 1012 && code <= 2999) {
            return false;
        }
        return true;
    }
}
