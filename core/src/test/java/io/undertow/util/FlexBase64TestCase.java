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

package io.undertow.util;


import io.undertow.testutils.category.UnitTest;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTest.class)
public class FlexBase64TestCase {

    @Test
    public void testReadStopsAtTerminator() throws Exception {
        String source = "ZWxsbw===";
        byte[] target = new byte[1024];
        final FlexBase64.Decoder decoder = FlexBase64.createDecoder();
        int read = decoder.decode(source, 0, source.length(), target, 0, target.length);
        Assert.assertEquals(4, read);
        Assert.assertEquals("ello", new String(target, 0, read));
        Assert.assertEquals(8, decoder.getLastInputPosition());

    }

    @Test
    public void testEncodeURLWithByteBufferUsesUrlTable() {
        ByteBuffer source = ByteBuffer.wrap(new byte[]{0, 0x01, 0, 0, 0x10, 0, 0, 2, 0, 0, 0, 0x01, 0, 0x04, 0, 0, (byte) 0xff, (byte) 0xff, 0, 0x05, 0, 0, 0x40, 0});
        String target = FlexBase64.encodeStringURL(source, false);
        Assert.assertEquals("AAEAABAAAAIAAAABAAQAAP__AAUAAEAA", target);
    }
}
