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

package io.undertow.protocols.http2;


import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;




/**
 * @author Marek Jusko
 */
@Category(UnitTest.class)
public class HpackHuffmanDecodingStringLiteralRepresentation {

    /**
     * Sends a Huffman-encoded string literal representation containing the EOS symbol.
     * <p>
     * Requirement: The endpoint MUST treat this as a decoding error.
     */
    @Test
    public void testStringLiteralContainingEOS() throws HpackException {
        byte[] data = new byte[]{0x00, (byte) 0x85, (byte) 0xf2, (byte) 0xb2, 0x4a, (byte) 0x84, (byte) 0xff, (byte) 0x87, 0x49, (byte) 0x51, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfa, 0x7f};
        HpackDecoder decoder = new HpackDecoder(256);

        Assert.assertThrows(HpackException.class, () -> decoder.decode(ByteBuffer.wrap(data), false));
    }

    /**
     * Sends a Huffman-encoded string literal representation with padding longer than 7 bits.
     * <p>
     * Requirement: The endpoint MUST treat this as a decoding error.
     */
    @Test
    public void testStringLiteralPaddingLongerThan7Bits() throws HpackException {
        byte[] data = new byte[]{0x00, (byte) 0x85, (byte) 0xf2, (byte) 0xb2, 0x4a, (byte) 0x84, (byte) 0xff, (byte) 0x84, 0x49, (byte) 0x50, (byte) 0x9f, (byte) 0xff};
        HpackDecoder decoder = new HpackDecoder(256);

        Assert.assertThrows(HpackException.class, () -> decoder.decode(ByteBuffer.wrap(data), false));
    }

    /**
     * Sends a Huffman-encoded string literal representation padded by zero.
     * <p>
     * Requirement: The endpoint MUST treat this as a decoding error.
     */
    @Test
    public void testStringLiteralPaddedByZero() throws HpackException {
        byte[] data = new byte[]{0x00, (byte) 0x85, (byte) 0xf2, (byte) 0xb2, 0x4a, (byte) 0x84, (byte) 0xff, (byte) 0x83, 0x49, (byte) 0x50, (byte) 0x90};
        HpackDecoder decoder = new HpackDecoder(256);

        Assert.assertThrows(HpackException.class, () -> decoder.decode(ByteBuffer.wrap(data), false));
    }

}
