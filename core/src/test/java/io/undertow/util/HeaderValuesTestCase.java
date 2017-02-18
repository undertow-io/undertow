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

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Category(UnitTest.class)
public final class HeaderValuesTestCase {

    @Test
    public void testBasic() {
        final HeaderValues headerValues = new HeaderValues(Headers.DEFLATE);
        assertEquals(0, headerValues.size());
        assertTrue(headerValues.isEmpty());
        assertFalse(headerValues.iterator().hasNext());
        assertFalse(headerValues.descendingIterator().hasNext());
        assertFalse(headerValues.listIterator().hasNext());
        assertFalse(headerValues.listIterator(0).hasNext());
        assertNull(headerValues.peek());
        assertNull(headerValues.peekFirst());
        assertNull(headerValues.peekLast());
    }

    @Test
    public void testAdd() {
        HeaderValues headerValues = new HeaderValues(Headers.HOST);
        assertTrue(headerValues.add("Foo"));
        assertTrue(headerValues.contains("Foo"));
        assertTrue(headerValues.contains(new String("Foo")));
        assertFalse(headerValues.contains("Bar"));
        assertFalse(headerValues.isEmpty());
        assertEquals(1, headerValues.size());
        assertEquals("Foo", headerValues.peek());
        assertEquals("Foo", headerValues.peekFirst());
        assertEquals("Foo", headerValues.peekLast());
        assertEquals("Foo", headerValues.get(0));

        assertTrue(headerValues.offerFirst("First!"));
        assertTrue(headerValues.contains("First!"));
        assertTrue(headerValues.contains("Foo"));
        assertEquals(2, headerValues.size());
        assertEquals("First!", headerValues.peek());
        assertEquals("First!", headerValues.peekFirst());
        assertEquals("First!", headerValues.get(0));
        assertEquals("Foo", headerValues.peekLast());
        assertEquals("Foo", headerValues.get(1));

        assertTrue(headerValues.offerLast("Last!"));
        assertTrue(headerValues.contains("Last!"));
        assertTrue(headerValues.contains("Foo"));
        assertTrue(headerValues.contains("First!"));
        assertEquals(3, headerValues.size());
        assertEquals("First!", headerValues.peek());
        assertEquals("First!", headerValues.peekFirst());
        assertEquals("First!", headerValues.get(0));
        assertEquals("Foo", headerValues.get(1));
        assertEquals("Last!", headerValues.peekLast());
        assertEquals("Last!", headerValues.get(2));
    }

}
