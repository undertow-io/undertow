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
package io.undertow.websockets.frame;

import java.nio.ByteBuffer;

import org.xnio.Buffers;

/**
 * 
 * Abstract base class for all WebSocket frames.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class WebSocketFrame {


    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    private final boolean finalFragment;

    /**
     * Contents of this frame
     */
    private ByteBuffer data;

    /**
     * Creates a new WebSockets frame
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param binaryData
     *            the {@link ByteBuffer} that holds the data of the WebSocket frame
     */
    protected WebSocketFrame(boolean finalFragment, ByteBuffer data) {
        this.finalFragment = finalFragment;
        setBinaryData(data);
    }

    /**
     * Calls {@link #WebSocketFrame(boolean, int, ByteBuffer)} with an empty {@link ByteBuffer}.
     * 
     */
    protected WebSocketFrame(boolean finalFragment) {
        this(finalFragment, Buffers.EMPTY_BYTE_BUFFER);
    }


    /**
     * Returns binary data of the {@link WebSocketFrame}. Be aware that its a <strong>read-only</code>
     * {@link ByteBuffer}, so no modification is possible.
     */
    public final ByteBuffer getBinaryData() {
        return data;
    }

    /**
     * Set the binary data of this {@link WebSocketFrame}.
     * 
     * @param data      The {@link ByteBuffer} which represent the binary data of this {@link WebSocketFrame}. If
     *                  <code>null</code> is used, it will just set the data to an empty {@link ByteBuffer}.
     */
    protected void setBinaryData(ByteBuffer data) {
        if (data == null) {
            this.data = Buffers.EMPTY_BYTE_BUFFER;
        } else {
            this.data = data.asReadOnlyBuffer();
        }
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public final boolean isFinalFragment() {
        return finalFragment;
    }
}
