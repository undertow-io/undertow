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
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class HpackHuffmanEncodingUnitTestCase {

    @Test
    public void testHuffmanEncoding() throws HpackException {
        runTest("Hello World", ByteBuffer.allocate(100), true);
        runTest("Hello World", ByteBuffer.allocate(3), false);
        runTest("\\randomSpecialsChars~\u001D", ByteBuffer.allocate(100), true);
        runTest("\\~\u001D", ByteBuffer.allocate(100), false); //encoded form is larger than the original string
    }


    @Test
    public void testHuffmanEncodingLargeString() throws HpackException {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 100; ++i) {
            sb.append("Hello World");
        }
        runTest(sb.toString(), ByteBuffer.allocate(10000), true); //encoded form is larger than the original string
    }

    void runTest(String string, ByteBuffer buffer, boolean bufferBigEnough) throws HpackException {
        boolean res = HPackHuffman.encode(buffer, string, false);
        if(!bufferBigEnough) {
            Assert.assertFalse(res);
            return;
        }
        Assert.assertTrue(res);
        buffer.flip();
        Assert.assertTrue(((1 << 7) & buffer.get(0)) != 0); //make sure the huffman bit is set
        int length = Hpack.decodeInteger(buffer, 7);
        StringBuilder sb = new StringBuilder();
        HPackHuffman.decode(buffer, length, sb);
        Assert.assertEquals(string, sb.toString());
    }
}
