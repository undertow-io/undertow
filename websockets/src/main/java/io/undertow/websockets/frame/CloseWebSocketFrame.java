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


import io.undertow.websockets.WebSocketUtils;

import java.nio.ByteBuffer;

/**
 * 
 * A Close WebSocket frame.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class CloseWebSocketFrame extends WebSocketFrame {


    /**
     * Creates a new close frame with closing status code and reason text
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param statusCode
     *            Short status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(boolean finalFragment, short statusCode, String reasonText) {
        super(finalFragment);
        ByteBuffer buffer = WebSocketUtils.fromUtf8String(reasonText);
        ByteBuffer binaryData = ByteBuffer.allocate(2 + buffer.remaining());
        binaryData.putShort(statusCode);
        if (buffer.remaining() > 0) {
            binaryData.put(buffer);
        }
        binaryData.flip();
        setBinaryData(binaryData);
    }

    /**
     * Creates a new close frame
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param binaryData
     *            the content of the frame. Must be 2 byte integer followed by optional UTF-8 encoded string.
     */
    public CloseWebSocketFrame(boolean finalFragment, ByteBuffer binaryData) {
        super(finalFragment, binaryData);
    }

    /**
     * Returns the closing status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. 
     */
    public short getStatusCode() {
        ByteBuffer binaryData = getBinaryData();
        if (binaryData == null || binaryData.capacity() == 0) {
            return -1;
        }
        return binaryData.getShort(0);
    }

    /**
     * Returns the reason text as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a> If a reason
     * text is not supplied, an empty string is returned.
     */
    public String getReasonText() {
        ByteBuffer buffer = getBinaryData();
        if (buffer == null || buffer.capacity() <= 2) {
            return "";
        }        
        buffer.position(2);

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

    @Override
    public WebSocketFrameType getType() {
        return WebSocketFrameType.CLOSE;
    }
}
