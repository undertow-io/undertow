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

package io.undertow.websockets.extensions;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;

import java.io.IOException;

/**
 * Base interface for WebSocket Extensions implementation.
 * <p/>
 * It interacts at the connection phase. It is responsible to apply extension's logic before to write and after to read to/from
 * a WebSocket Endpoint.
 * <p/>
 * Several extensions can be present in a WebSocket Endpoint being executed in a chain pattern.
 * <p/>
 * Extension state is stored per WebSocket connection.
 *
 * @author Lucas Ponce
 */
public interface ExtensionFunction {

    /**
     * Bitmask for RSV1 bit used in extensions.
     */
    int RSV1 = 0x04;

    /**
     * Bitmask for RSV2 bit used in extensions.
     */
    int RSV2 = 0x02;
    /**
     * Bitmask for RSV3 bit used in extensions.
     */
    int RSV3 = 0x01;

    /**
     * Validate if current extension defines a new WebSocket Opcode.
     *
     * @return {@code true} if current extension defines specific Opcode
     * {@code false} is current extension does not define specific Opcode
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-13#section-5.2">WebSocket Base Framing Protocol Reference</a>
     */
    boolean hasExtensionOpCode();

    /**
     * Add RSV bits (RSV1, RSV2, RSV3) to the current rsv status.
     *
     * @param rsv current RSV bits status
     * @return rsv status
     */
    int writeRsv(int rsv);

    /**
     * Transform the supplied buffer per this extension. The buffer can be modified in place, or a new pooled buffer
     * can be returned (in which case be sure to free the original buffer
     *
     * @param pooledBuffer Buffer to transform
     * @param channel      working channel
     * @return transformed buffer (may be the same one, just with modified contents)
     * @throws IOException
     */
    PooledByteBuffer transformForWrite(PooledByteBuffer pooledBuffer, StreamSinkFrameChannel channel, boolean lastFrame) throws IOException;

    /**
     * Transform the supplied buffer per this extension. The buffer can be modified in place, or a new pooled buffer
     * can be returned (in which case be sure to free the original buffer
     *
     * @param pooledBuffer Buffer to transform
     * @param channel      working channel
     * @param lastFragmentOfMessage If this frame is the last fragment of a message. Note that this may not be received for every message, if the message ends with an empty frame
     * @return transformed buffer (may be the same one, just with modified contents)
     * @throws IOException
     */
    PooledByteBuffer transformForRead(PooledByteBuffer pooledBuffer, StreamSourceFrameChannel channel, boolean lastFragmentOfMessage) throws IOException;

    /**
     * Dispose this function. Called upon connection closure
     */
    void dispose();
}
