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

import java.io.IOException;

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;

/**
 * Base interface for WebSocket Extensions implementation.
 * <p>
 * It interacts at the connection phase. It is responsible to apply extension's logic before to write and after to read to/from
 * a WebSocket Endpoint.
 * <p>
 * Several extensions can be present in a WebSocket Endpoint being executed in a chain pattern.
 * <p>
 * Extension state is stored per WebSocket connection.
 *
 * @author Lucas Ponce
 */
public interface ExtensionFunction {

    /**
     * Indicate if this extension is configured for client context.
     * <p>
     * Server/client contexts can affect in how parameters are negotiated.
     *
     * @return {@code true} if current extension is configured for client context;
     *         {@code false} if current extension is configure for server context
     */
    boolean isClient();

    /**
     * Validate if current extension defines a new WebSocket Opcode.
     *
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-13#section-5.2">WebSocket Base Framing Protocol Reference</a>
     *
     * @return {@code true} if current extension defines specific Opcode
     *         {@code false} is current extension does not define specific Opcode
     */
    boolean hasExtensionOpCode();


    /**
     * Add RSV bits (RSV1, RSV2, RSV3) to the current rsv status.
     *
     * @param rsv current RSV bits status
     * @return    rsv status
     */
    int writeRsv(int rsv);

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
     * Is called on the {@link ExtensionByteBuffer} before a write operation completes.
     * <p>
     * {@link ExtensionByteBuffer} is used as a wrapper of the original {@link java.nio.ByteBuffer} prepared for a write operation
     * with a WebSocket Endpoint.
     * <p>
     * An extension can expand content beyond capacity of original {@code ByteBuffer}.
     * <p>
     * An extension will process an end of message on {@link ExtensionFunction#beforeFlush(StreamSinkFrameChannel, ExtensionByteBuffer, int, int)} invocation.
     *
     * @param channel       the {@link StreamSinkFrameChannel} used on this operation
     * @param extBuf        the {@link ExtensionByteBuffer} to operate on
     * @param position      the index in the {@link ExtensionByteBuffer} to start from
     * @param length        the number of bytes to operate on
     * @throws IOException  thrown if an error occurs
     */
    void beforeWrite(final StreamSinkFrameChannel channel, final ExtensionByteBuffer extBuf, final int position, final int length) throws IOException;

    /**
     * Is called on the {@link ExtensionByteBuffer} before a flush() operation.
     * <p>
     * It processes an end of message for a write operation.
     * <p>
     * Extensions may write a final content as padding at the end of the message.
     * <p>
     * {@link ExtensionByteBuffer} is used as a wrapper of the original {@link java.nio.ByteBuffer} prepared for a write operation
     * with a WebSocket Endpoint.
     * <p>
     * An extension can expand content beyond capacity of original {@code ByteBuffer}.
     *
     * @param channel       the {@link StreamSinkFrameChannel} used on this operation
     * @param extBuf        the {@link ExtensionByteBuffer} to operate on
     * @param position      the index in the {@link ExtensionByteBuffer} to start from
     * @param length        the number of bytes to operate on
     *
     * @throws IOException  thrown if an error occurs
     */
    void beforeFlush(final StreamSinkFrameChannel channel, final ExtensionByteBuffer extBuf, final int position, final int length) throws IOException;

    /**
     * Is called on the {@link ExtensionByteBuffer} after a read operation completes.
     * <p>
     * {@link ExtensionByteBuffer} is used as a wrapper of the original {@link java.nio.ByteBuffer} resulted of a read operation
     * with a WebSocket Endpoint.
     * <p>
     * An extension can expand content beyond capacity of original {@code ByteBuffer}.
     * <p>
     * An extension will process an end of message when {@code length == -1 } .
     *
     * @param channel       the {@link StreamSourceFrameChannel} used on this operation
     * @param extBuf        the {@link ExtensionByteBuffer} to operate on
     * @param position      the index in the {@link ExtensionByteBuffer} to start from
     * @param length        the number of bytes to operate on
     *
     * @throws IOException  thrown if an error occurs
     */
    void afterRead(final StreamSourceFrameChannel channel, final ExtensionByteBuffer extBuf, final int position, final int length) throws IOException;
}
