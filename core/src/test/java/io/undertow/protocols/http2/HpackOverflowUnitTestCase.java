/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import io.undertow.protocols.http2.HpackDecoder.HeaderEmitter;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Kanatoko
 */
@Category(UnitTest.class)
public class HpackOverflowUnitTestCase {

    @Test
    public void testStringLiteralContainingEOS() throws HpackException {
        final int countOfSameNames = testImpl("X-Header-1", "X-Header-1");
        final int countOfDifferentNames = testImpl("X-Header-1", "X-Header-2");
        Assert.assertEquals(2, countOfSameNames);
        Assert.assertEquals(2, countOfDifferentNames);
    }

    private static int testImpl(final String name1, final String name2) {
        final HeaderMap headerMap = new HeaderMap();
        headerMap.add(new HttpString(name1), getStr(300, (byte) 0x41));// overflow
        headerMap.add(new HttpString(name2), getStr(150, (byte) 0x42));// skipped if name1 equals name2
        final HpackEncoder encoder = new HpackEncoder(4096);
        final ByteBuffer buffer1 = ByteBuffer.allocate(256);

        // overflow
        HpackEncoder.State result = encoder.encode(headerMap, buffer1);

        Assert.assertEquals(HpackEncoder.State.OVERFLOW, result);
        final ByteBuffer buffer2 = ByteBuffer.allocate(512);

        // complete
        result = encoder.encode(headerMap, buffer2);
        Assert.assertEquals(HpackEncoder.State.COMPLETE, result);

        final HpackDecoder decoder = new HpackDecoder(4096);

        final AtomicInteger count = new AtomicInteger();
        try {
            buffer2.flip();
            decoder.setHeaderEmitter(new HeaderEmitter() {

                @Override
                public void emitHeader(HttpString name, String value, boolean neverIndex) throws HpackException {
                    count.incrementAndGet();

                }
            });
            decoder.decode(buffer2, false);
        } catch (HpackException e) {
            e.printStackTrace();
        }
        return count.get();
    }

    private static String getStr(final int length, byte content) {
        final byte[] buf = new byte[length];
        Arrays.fill(buf, content);
        return new String(buf);
    }

}
