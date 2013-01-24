/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.api;

/**
 * The reason of the CLOSE frame.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class CloseReason {
   /*
    * For the exact meaning of the codes refer to the <a href="http://tools.ietf.org/html/rfc6455#section-7.4">WebSocket
    * RFC Section 7.4</a>.
    */
    public static final int NORMAL_CLOSURE = 1000;
    public static final int GOING_AWAY = 1001;
    public static final int PROTOCOL_ERROR = 1003;
    public static final int MSG_CONTAINS_INVALID_DATA = 1007;
    public static final int MSG_VIOLATES_POLICY = 1008;
    public static final int MSG_TOO_BIG = 1009;
    public static final int MISSING_EXTENSIONS = 1010;
    public static final int UNEXPECTED_ERROR = 1011;

    /**
     * NO CloseReason
     */
    public static final CloseReason NONE = null;

    /**
     * CloseReason for NORMAL close
     */
    public static final CloseReason NORMAL = new CloseReason(NORMAL_CLOSURE);

    private final int code;
    private final String reason;

    /**
     * Create a new {@link CloseReason} with now reasonText.
     */
    public CloseReason(int code) {
        this(code, null);
    }

    /**
     * Creates a new {@link CloseReason}
     *
     * @param code
     *          the status code to use
     * @param reason
     *          the reason text to use or {@code null} if non should be used.
     * @throws IllegalArgumentException
     *          if the status code is not valid
     */
    public CloseReason(int code, String reason) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("Invalid close status code " + code);
        }
        this.code = code;
        this.reason = reason;
    }

    /**
     * Return the status code to use
     */
    public int getStatusCode() {
        return code;
    }

    /**
     * Return the reason text or {@code null} if none should be used.
     */
    public String getReasonText() {
        return reason;
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
