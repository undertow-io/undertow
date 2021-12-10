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
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Category(UnitTest.class)
public final class HeaderMapTestCase {

    private static final List<HttpString> HTTP_STRING_LIST = Arrays.asList(Headers.CONNECTION, Headers.HOST, Headers.UPGRADE, Headers.CONTENT_MD5, Headers.KEEP_ALIVE, Headers.RESPONSE_AUTH, Headers.CONTENT_DISPOSITION, Headers.DEFLATE, Headers.NEGOTIATE, Headers.USER_AGENT, Headers.REFERER, Headers.TRANSFER_ENCODING, Headers.FROM);

    @Test
    public void testInitial() {
        final HeaderMap headerMap = new HeaderMap();
        assertEquals(0, headerMap.size());
        assertEquals(-1L, headerMap.fastIterate());
        assertFalse(headerMap.iterator().hasNext());
    }

    @Test
    public void testMixedCase() {
        final HeaderMap headerMap = new HeaderMap();
        headerMap.add(new HttpString("Aa"), "A");
        headerMap.add(new HttpString("aa"), "a");
        assertArrayEquals(headerMap.get(new HttpString("aa")).toArray(), new String[]{"A", "a"});
        assertArrayEquals(headerMap.get(new HttpString("Aa")).toArray(), new String[]{"A", "a"});
        assertArrayEquals(headerMap.get(new HttpString("AA")).toArray(), new String[]{"A", "a"});
    }

    @Test
    public void testSimple() {
        final HeaderMap headerMap = new HeaderMap();
        headerMap.add(Headers.HOST, "yay.undertow.io");
        assertTrue(headerMap.contains(Headers.HOST));
        assertTrue(headerMap.contains("host"));
        assertEquals(1, headerMap.size());
        assertNotEquals(-1L, headerMap.fastIterate());
        assertEquals(-1L, headerMap.fiNext(headerMap.fastIterate()));
        assertEquals(Headers.HOST, headerMap.fiCurrent(headerMap.fastIterate()).getHeaderName());
        assertEquals("yay.undertow.io", headerMap.getFirst(Headers.HOST));
        assertEquals("yay.undertow.io", headerMap.getLast(Headers.HOST));
        assertEquals("yay.undertow.io", headerMap.get(Headers.HOST, 0));
        headerMap.remove("host");
        assertEquals(0, headerMap.size());
    }

    @Test
    public void testGrowing() {
        final HeaderMap headerMap = new HeaderMap();
        for (HttpString item : HTTP_STRING_LIST) {
            for (int i = 0; i < (item.hashCode() & 7) + 1; i++)
                headerMap.add(item, "Test value");
        }
        for (HttpString item : HTTP_STRING_LIST) {
            assertTrue(String.format("Missing %s (hash %08x)", item, Integer.valueOf(item.hashCode())), headerMap.contains(item));
            assertNotNull(headerMap.get(item));
            assertEquals((item.hashCode() & 7) + 1, headerMap.get(item).size());
            assertEquals("Test value", headerMap.getFirst(item));
            assertEquals("Test value", headerMap.getLast(item));
        }
        assertEquals(HTTP_STRING_LIST.size(), headerMap.size());
        for (HttpString item : HTTP_STRING_LIST) {
            assertTrue(headerMap.contains(item));
            assertNotNull(headerMap.remove(item));
            assertFalse(headerMap.contains(item));
        }
        assertEquals(0, headerMap.size());
    }

    @Test
    public void testCollision() {
        HeaderMap headerMap = new HeaderMap();
        headerMap.put(new HttpString("Link"), "a");
        headerMap.put(new HttpString("Rest"), "b");
        assertEquals("a", headerMap.getFirst(new HttpString("Link")));
        assertEquals("b", headerMap.getFirst(new HttpString("Rest")));
        assertEquals("a", headerMap.getFirst("Link"));
        assertEquals("b", headerMap.getFirst("Rest"));
    }
}
