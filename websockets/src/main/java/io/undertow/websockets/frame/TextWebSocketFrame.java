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

import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketUtils;

import java.nio.ByteBuffer;

import org.xnio.Buffers;

/**
 * 
 * A Text WebSocket frame, which can be used to transfer UTF-8 encoded Strings.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class TextWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty text frame.
     */
    public TextWebSocketFrame() {
        super(true, Buffers.EMPTY_BYTE_BUFFER);
    }

    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param text
     *            String to put in the frame
     */
    public TextWebSocketFrame(String text) {
        super(true);
        setText(text);
    }

    /**
     * Creates a new text frame with the specified binary data. The final fragment flag is set to true.
     *
     * @param binaryData
     *            the content of the frame. Must be UTF-8 encoded
     */
    public TextWebSocketFrame(ByteBuffer binaryData) {
        super(true, binaryData);
    }

    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param text
     *            String to put in the frame
     */
    public TextWebSocketFrame(boolean finalFragment, int rsv, String text) {
        super(finalFragment);
        setText(text);
    }

    /**
     * Creates a new text frame with the specified binary data. The final fragment flag is set to true.
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param binaryData
     *            the content of the frame. Must be UTF-8 encoded
     */
    public TextWebSocketFrame(boolean finalFragment, ByteBuffer binaryData) {
        super(finalFragment, binaryData);
    }

    /**
     * Returns the text data in this frame as UTF-8 encoded string
     */
    public String getText() {
        if (getBinaryData() == null) {
            return null;
        }
        ByteBuffer buffer = getBinaryData();
        if (buffer.hasArray()) {
            // if its backed by an array just safe one byte-copy operation and
            // directly use the backed array
            return new String(buffer.array(), buffer.arrayOffset(), buffer.remaining(), WebSocketUtils.UTF_8);
        } else {
            // not backed by a byte array so need to bulk transfer the data
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            buffer.position(0);
            return new String(bytes, WebSocketUtils.UTF_8);
        }
    }

    /**
     * Sets the string for this frame
     *
     * @param text The Text which is stored as data in the {@link TextWebSocketFrame}
     * 
     */
    public void setText(String text) {
        setBinaryData(WebSocketUtils.fromUtf8String(text));
    }

    @Override
    public WebSocketFrameType getType() {
        return WebSocketFrameType.TEXT;
    }
}
