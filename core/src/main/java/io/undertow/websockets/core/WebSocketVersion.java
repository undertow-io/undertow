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


import io.undertow.util.AttachmentKey;

/**
 * <p>
 * Enum which list all the different versions of the WebSocket specification (to the current date).
 * </p>
 * <p>
 * A specification is tied to one wire protocol version but a protocol version may have use by more than 1 version of
 * the specification.
 * </p>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public enum WebSocketVersion {

    /**
     * Unknown version of the protocol
     */
    UNKNOWN,

    /**
     * <a href= "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00"
     * >draft-ietf-hybi-thewebsocketprotocol- 00</a>.
     */
    V00,

    /**
     * <a href= "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-10"
     * >draft-ietf-hybi-thewebsocketprotocol- 07</a>
     */
    V07,

    /**
     * <a href= "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-10"
     * >draft-ietf-hybi-thewebsocketprotocol- 10</a>
     */
    V08,

    /**
     * <a href="http://tools.ietf.org/html/rfc6455">RFC 6455</a>. This was originally <a href=
     * "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-17" >draft-ietf-hybi-thewebsocketprotocol-
     * 17</a>
     */
    V13;

    /**
     * Returns a {@link String} representation of the {@link WebSocketVersion} that can be used in the HTTP Headers.
     */
    public String toHttpHeaderValue() {
        if (this == V00) {
            return "0";
        }
        if (this == V07) {
            return "7";
        }
        if (this == V08) {
            return "8";
        }
        if (this == V13) {
            return "13";
        }
        // Should never hit here.
        throw new IllegalStateException("Unknown WebSocket version: " + this);
    }


    public static final AttachmentKey<WebSocketVersion> ATTACHMENT_KEY = AttachmentKey.create(WebSocketVersion.class);
}

