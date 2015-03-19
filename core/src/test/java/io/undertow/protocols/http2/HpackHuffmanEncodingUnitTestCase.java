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

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class HpackHuffmanEncodingUnitTestCase {

    @Test
    public void testHuffmanEncoding() throws HpackException {
        runTest("Hello World", ByteBuffer.allocate(100), true);
        runTest("Hello World", ByteBuffer.allocate(3), false);
        runTest("\\randomSpecialsChars~\u001D", ByteBuffer.allocate(100), true);
        runTest("\\~\u001D", ByteBuffer.allocate(100), false); //encoded form is larger than the original string

    }


    void runTest(String string, ByteBuffer buffer, boolean bufferBigEnough) throws HpackException {
        boolean res = HPackHuffman.encode(buffer, string);
        if(!bufferBigEnough) {
            Assert.assertFalse(res);
            return;
        }
        Assert.assertTrue(res);
        buffer.flip();
        int length = buffer.get() & 0xff;
        Assert.assertTrue(((1 << 7) & length) != 0);
        StringBuilder sb = new StringBuilder();
        HPackHuffman.decode(buffer, length & ~(1<<7), sb);
        Assert.assertEquals(string, sb.toString());
    }
}
