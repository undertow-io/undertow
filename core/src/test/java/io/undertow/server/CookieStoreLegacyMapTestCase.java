/*
 * Copyright The Undertow Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.undertow.server;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Tests for the deprecated {@link CookieStore#asLegacyMap()} functionality. This ensures backward compatibility with
 * the deprecated {@link HttpServerExchange#getRequestCookies()} and getResponseCookies() methods.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@SuppressWarnings({"removal", "ConstantValue", "RedundantCollectionOperation"})
@Category(UnitTest.class)
public class CookieStoreLegacyMapTestCase {

    /**
     * Tests that the legacy flattened map implements the {@link Map#values()} correctly.
     */
    @Test
    public void valuesIteration() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("SESSION", "abc123"));
        store.add(new CookieImpl("TRACKING", "xyz789"));
        store.add(new CookieImpl("LANG", "en"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        // This threw UnsupportedOperationException in Beta1
        final Collection<Cookie> values = legacyMap.values();
        Assert.assertNotNull("values() should not return null", values);
        Assert.assertEquals("Should have 3 cookies", 3, values.size());

        // Verify we can iterate
        int count = 0;
        for (Cookie cookie : values) {
            Assert.assertNotNull("Cookie should not be null", cookie);
            count++;
        }
        Assert.assertEquals("Should iterate over all cookies", 3, count);
    }

    /**
     * Test that the legacy flattened map implements the {@link Map#entrySet()} correctly.
     */
    @Test
    public void entrySetIteration() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("FOO", "value1"));
        store.add(new CookieImpl("BAR", "value2"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        final Set<Map.Entry<String, Cookie>> entries = legacyMap.entrySet();
        Assert.assertNotNull("entrySet() should not return null", entries);
        Assert.assertEquals("Should have 2 entries", 2, entries.size());

        // Verify iteration
        int count = 0;
        for (Map.Entry<String, Cookie> entry : entries) {
            Assert.assertNotNull("Entry key should not be null", entry.getKey());
            Assert.assertNotNull("Entry value should not be null", entry.getValue());
            count++;
        }
        Assert.assertEquals("Should iterate over all entries", 2, count);
    }

    /**
     * Tests that the legacy map will return the first cookie on a duplicate cookie name, but different paths. This
     * is for RFC-2109.
     */
    @Test
    public void multipleRFC2109Cookies() {
        final CookieStore store = new CookieStore();
        final Cookie first = new CookieImpl("CUSTOMER", "JOE").setPath("/acme");
        final Cookie second = new CookieImpl("CUSTOMER", "MONICA").setPath("/");

        store.add(first);
        store.add(second);
        store.add(new CookieImpl("SESSION", "abc"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        // We should only have one entry as the flattened map should remove the second entry added
        Assert.assertEquals("Should have 2 cookies", 2, legacyMap.size());

        final Cookie result = legacyMap.get("CUSTOMER");
        Assert.assertNotNull("Should find CUSTOMER cookie", result);
        // Should return the last one added
        Assert.assertEquals("Should return last cookie", "MONICA", result.getValue());
        Assert.assertEquals("Should have correct path", "/", result.getPath());
        Assert.assertEquals("Values should have 2 entries", 2, legacyMap.values().size());
        Assert.assertEquals("EntrySet should have 2 entries", 2, legacyMap.entrySet().size());
    }

    /**
     * Test that duplicate cookies (same name+path+domain) are replaced in the flat map.
     */
    @Test
    public void duplicateRFC6265Cookies() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("SESSION", "first"));
        store.add(new CookieImpl("SESSION", "second"));  // Same name+path+domain - replaces
        store.add(new CookieImpl("SESSION", "third"));   // Replaces again

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        // We should only have one entry as the flattened map should remove the second entry added
        Assert.assertEquals("Should have 1 cookies", 1, legacyMap.size());

        Cookie result = legacyMap.get("SESSION");
        Assert.assertNotNull("Should find SESSION cookie", result);
        Assert.assertEquals("Should have the last value", "third", result.getValue());
    }

    /**
     * Test {@link Map#put(Object, Object)} replaces all cookies with that name.
     */
    @Test
    public void putReplacesAllCookies() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("FOO", "original").setPath("/a"));
        store.add(new CookieImpl("FOO", "another").setPath("/b"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        final Cookie newCookie = new CookieImpl("FOO", "replacement");
        final Cookie old = legacyMap.put("FOO", newCookie);

        Assert.assertNotNull("put() should return old value", old);
        Assert.assertEquals("Should return last old cookie", "another", old.getValue());

        // After put, only the new cookie should exist
        final Cookie result = legacyMap.get("FOO");
        Assert.assertEquals("Should have new value", "replacement", result.getValue());

        // Verify only one FOO cookie remains in the store
        Assert.assertEquals("Store should have only one cookie", 1, store.get("FOO").size());
    }

    /**
     * Test {@link Map#remove(Object)} removes all cookies with that name.
     */
    @Test
    public void removeRemovesAllCookies() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("BAR", "value1").setPath("/x"));
        store.add(new CookieImpl("OTHER", "keep"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        final Cookie removed = legacyMap.remove("BAR");
        Assert.assertNotNull("remove() should return removed value", removed);
        Assert.assertEquals("Should return first cookie", "value1", removed.getValue());

        Assert.assertNull("BAR should be gone", legacyMap.get("BAR"));
        Assert.assertEquals("OTHER should remain", "keep", legacyMap.get("OTHER").getValue());

        // Verify store is updated
        Assert.assertTrue("Store should not have BAR cookies", store.get("BAR").isEmpty());
    }

    /**
     * Test {@link Map#containsKey(Object)} works correctly.
     */
    @Test
    public void containsKey() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("EXISTS", "value"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        Assert.assertTrue("Should contain EXISTS", legacyMap.containsKey("EXISTS"));
        Assert.assertFalse("Should not contain MISSING", legacyMap.containsKey("MISSING"));
    }

    /**
     * Test {@link Map#containsValue(Object)} works correctly.
     */
    @Test
    public void containsValue() {
        final CookieStore store = new CookieStore();
        final Cookie cookie = new CookieImpl("TEST", "value");
        store.add(cookie);
        store.add(new CookieImpl("OTHER", "different"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        // Note: containsValue checks object equality, not just the value string
        Assert.assertTrue("Should contain the TEST cookie",
                legacyMap.containsValue(new CookieImpl("TEST", "value")));
        Assert.assertFalse("Should not contain non-existent cookie",
                legacyMap.containsValue(new CookieImpl("FAKE", "fake")));
    }

    /**
     * Test {@link Map#size()} and {@link Map#isEmpty()} work correctly.
     */
    @Test
    public void sizeAndEmpty() {
        final CookieStore store = new CookieStore();
        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        Assert.assertTrue("New map should be empty", legacyMap.isEmpty());
        Assert.assertEquals("New map should have size 0", 0, legacyMap.size());

        store.add(new CookieImpl("A", "1"));
        store.add(new CookieImpl("B", "2"));

        Assert.assertFalse("Map should not be empty", legacyMap.isEmpty());
        Assert.assertEquals("Map should have size 2", 2, legacyMap.size());
    }

    /**
     * Test {@link Map#clear()} removes all cookies.
     */
    @Test
    public void clear() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("A", "1"));
        store.add(new CookieImpl("B", "2"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();
        Assert.assertEquals("Should have 2 cookies", 2, legacyMap.size());

        legacyMap.clear();

        Assert.assertTrue("Map should be empty", legacyMap.isEmpty());
        Assert.assertTrue("Store should be empty", store.isEmpty());
    }

    /**
     * Test {@link Map#putAll(Map)}} works correctly.
     */
    @Test
    public void putAll() {
        final CookieStore store = new CookieStore();
        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        final Map<String, Cookie> toAdd = Map.of(
                "COOKIE1", new CookieImpl("COOKIE1", "value1"),
                "COOKIE2", new CookieImpl("COOKIE2", "value2")
        );

        legacyMap.putAll(toAdd);

        Assert.assertEquals("Should have 2 cookies", 2, legacyMap.size());
        Assert.assertEquals("COOKIE1 should exist", "value1", legacyMap.get("COOKIE1").getValue());
        Assert.assertEquals("COOKIE2 should exist", "value2", legacyMap.get("COOKIE2").getValue());
    }

    /**
     * Test {@link Map#keySet()} returns cookie names.
     */
    @Test
    public void keySet() {
        final CookieStore store = new CookieStore();
        store.add(new CookieImpl("FOO", "1"));
        store.add(new CookieImpl("BAR", "2"));

        final Map<String, Cookie> legacyMap = store.asLegacyMap();
        final Set<String> keys = legacyMap.keySet();

        Assert.assertEquals("Should have 2 keys", 2, keys.size());
        Assert.assertTrue("Should contain FOO", keys.contains("FOO"));
        Assert.assertTrue("Should contain BAR", keys.contains("BAR"));
    }

    /**
     * Test that the legacy map reflects changes to the underlying store.
     */
    @Test
    public void mutability() {
        final CookieStore store = new CookieStore();
        final Map<String, Cookie> legacyMap = store.asLegacyMap();

        Assert.assertTrue("Should start empty", legacyMap.isEmpty());

        // Add to store
        store.add(new CookieImpl("NEW", "cookie"));

        Assert.assertFalse("Should not be empty", legacyMap.isEmpty());
        Assert.assertEquals("Should see new cookie", "cookie", legacyMap.get("NEW").getValue());
        Assert.assertEquals("The store should have the cookie", "cookie", store.get("NEW").get(0).getValue());
    }
}
