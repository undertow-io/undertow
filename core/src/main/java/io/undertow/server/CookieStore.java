/*
 * Copyright The Undertow Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.undertow.server;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.server.handlers.Cookie;

/**
 * A store for cookies indexed by the cookie name, allowing multiple cookies with the same name, but different
 * path/domain combinations (RFC-2109 support).
 * <p>
 * <strong>Thread Safety:</strong> This class is <em>NOT</em> thread-safe by design. The {@link HttpServerExchange}
 * guarantees only one thread accesses the exchange at a time.
 * </p>
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 * @since 2.4.0
 */
public class CookieStore implements Iterable<Cookie> {

    private final Map<String, Deque<Cookie>> cookies;

    /**
     * Creates a new cookie store.
     */
    public CookieStore() {
        this.cookies = new LinkedHashMap<>();
    }

    /**
     * Returns the size of the cookie store.
     *
     * @return the number of cookies in the store
     */
    public int size() {
        // This may not end up being efficient for large amounts of cookies. However, that is likely an edge case.
        return cookies.values().stream().mapToInt(Deque::size).sum();
    }

    /**
     * Checks if the store is empty.
     *
     * @return {@code true} if the store is empty, otherwise {@code false}
     */
    public boolean isEmpty() {
        return cookies.isEmpty();
    }

    /**
     * Returns an immutable list of all the cookies in this store with the given name.
     *
     * @param name the name of the cookie
     *
     * @return an immutable list of cookies or an empty list if no cookies were associated with the name
     */
    public List<Cookie> get(final String name) {
        final Deque<Cookie> cookies = this.cookies.get(name);
        return cookies == null ? List.of() : List.copyOf(cookies);
    }

    /**
     * Removes the cookie from the store if it exists.
     *
     * @param cookie the cookie to remove
     *
     * @return {@code true} if the cookie was removed, {@code false} if the cookie was not found and therefore not removed
     */
    public boolean remove(final Cookie cookie) {
        final Deque<Cookie> cookies = this.cookies.get(cookie.getName());
        final boolean result = cookies != null && cookies.remove(cookie);
        if (cookies != null && cookies.isEmpty()) {
            this.cookies.remove(cookie.getName());
        }
        return result;
    }

    /**
     * Adds a cookie to the store.
     *
     * @param cookie the cookie to add, passing {@code null} does nothing
     *
     * @return this cookie store
     */
    public CookieStore add(final Cookie cookie) {
        if (cookie != null) {
            final Deque<Cookie> queue = cookies.computeIfAbsent(cookie.getName(), (ignore) -> new ArrayDeque<>());
            // Remove existing cookie with same name/path/domain and re-add to maintain uniqueness.
            queue.remove(cookie);
            queue.add(cookie);
        }
        return this;
    }

    /**
     * Returns the cookie store as a flat map, providing a single-valued view of the cookies.
     *
     * <p>
     * For cookies with identical name, path, and domain, only the most recently added cookie is included (duplicates
     * are automatically replaced).
     * </p>
     *
     * <p>
     * For cookies with the same name but different path/domain combinations, only one arbitrary cookie with that name
     * is returned. To access all cookies with the same name, use {@link #get(String)} instead.
     * </p>
     *
     * @return a map where each cookie name maps to a single Cookie instance
     *
     * @deprecated This method exists for backward compatibility with the deprecated
     * {@link HttpServerExchange#getRequestCookies()} and
     * {@link HttpServerExchange#getResponseCookies()} methods.
     * Use {@link #iterator()} or {@link #get(String)} instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "2.4.0")
    public Map<String, Cookie> asLegacyMap() {
        return new FlatMap(this);
    }

    /**
     * {@inheritDoc}
     * The iterator returned is immutable.
     */
    @Override
    public Iterator<Cookie> iterator() {
        if (cookies.isEmpty()) {
            return Collections.emptyIterator();
        }
        return new DelegatingIterator(this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[" +
                "cookies=" + cookies + ']';
    }

    private Deque<Cookie> getOrCreate(final String key) {
        return cookies.computeIfAbsent(key, ignore -> new ArrayDeque<>());
    }

    private Set<Map.Entry<String, Deque<Cookie>>> entrySet() {
        return cookies.entrySet();
    }

    private static class DelegatingIterator implements Iterator<Cookie> {
        private final Iterator<Map.Entry<String, Deque<Cookie>>> mapIterator;
        private Iterator<Cookie> current;

        private DelegatingIterator(final CookieStore cookieStore) {
            this.mapIterator = cookieStore.entrySet().iterator();
            advanceToNext();
        }

        @Override
        public boolean hasNext() {
            return current != null && current.hasNext();
        }

        @Override
        public Cookie next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final Cookie cookie = current.next();

            // If we just consumed the last cookie from this deque, advance to next
            if (!current.hasNext()) {
                advanceToNext();
            }
            return cookie;
        }

        private void advanceToNext() {
            while (mapIterator.hasNext()) {
                current = mapIterator.next().getValue().iterator();
                if (current.hasNext()) {
                    // Found a non-empty iterator
                    return;
                }
            }
            // No more cookies
            current = null;
        }
    }

    private static class FlatMap implements Map<String, Cookie> {
        private final CookieStore cookieStore;

        private FlatMap(final CookieStore cookieStore) {
            this.cookieStore = cookieStore;
        }

        @Override
        public int size() {
            // This is a flat map where we use only the first entry is used
            return cookieStore.cookies.size();
        }

        @Override
        public boolean isEmpty() {
            return cookieStore.isEmpty();
        }

        @Override
        public boolean containsKey(final Object key) {
            return cookieStore.cookies.containsKey(key);
        }

        @Override
        public boolean containsValue(final Object value) {
            if (value == null) {
                return false;
            }
            for (var entry : cookieStore.entrySet()) {
                final Deque<Cookie> queue = entry.getValue();
                if (value.equals(queue.peekLast())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Cookie get(final Object key) {
            final Deque<Cookie> queue = cookieStore.cookies.get(key);
            return queue == null ? null : queue.peekLast();
        }

        @Override
        public Cookie put(final String key, final Cookie value) {
            final Deque<Cookie> cookies = cookieStore.getOrCreate(key);
            final Cookie result = cookies.peekLast();
            cookies.clear();
            cookies.add(value);
            return result;
        }

        @Override
        public Cookie remove(final Object key) {
            final Deque<Cookie> queue = cookieStore.cookies.remove(key);
            return queue == null ? null : queue.peekLast();
        }

        @Override
        public void putAll(final Map<? extends String, ? extends Cookie> m) {
            m.forEach(this::put);
        }

        @Override
        public void clear() {
            cookieStore.cookies.clear();
        }

        @Override
        public Set<String> keySet() {
            return cookieStore.cookies.keySet();
        }

        @Override
        public Collection<Cookie> values() {
            return cookieStore.cookies.values().stream()
                    .map(Deque::getLast)
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public Set<Entry<String, Cookie>> entrySet() {
            return cookieStore.cookies.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), e.getValue().getLast()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
