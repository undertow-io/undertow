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

/**
 * The different WebSocketFrame types which are out there.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public enum WebSocketFrameType {

    /**
     * WebSocketFrame contains binary data
     */
    BINARY,

    /**
     * WebSocketFrame contains UTF-8 encoded {@link String}
     */
    TEXT,

    /**
     * WebSocketFrame which represent a ping request
     */
    PING,

    /**
     * WebSocketFrame which should be issued after a {@link #PING} was received
     */
    PONG,

    /**
     * WebSocketFrame which requests the close of the WebSockets connection
     */
    CLOSE,

    /**
     * WebSocketFrame which notify about more data to come
     */
    CONTINUATION,

    /**
     * Unknown frame-type
     */
    UNKOWN,

}
