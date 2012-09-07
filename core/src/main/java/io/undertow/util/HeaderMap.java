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

package io.undertow.util;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * This implementation sucks and is incomplete.  It's just here to illustrate.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderMap implements Iterable<String> {

    static class HeaderValue extends ArrayDeque<String> {
        private final String name;

        HeaderValue(final String name) {
            super(1);
            this.name = name;
        }

        HeaderValue(final String name, final String singleValue) {
            this(name);
            add(singleValue);
        }

        HeaderValue(final String name, final Collection<String> c) {
            super(c);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final HeaderValue strings = (HeaderValue) o;

            if (name != null ? !name.equals(strings.name) : strings.name != null) return false;
            if(strings.size() != size()) return false;
            Iterator<String> i1 = iterator();
            Iterator<String> i2 = strings.iterator();
            while (i1.hasNext()) {
                String n1 = i1.next();
                String n2 = i2.next();
                if(!n1.equals(n2)) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private final Map<String, HeaderValue> values = new SecureHashMap<String, HeaderValue>();

    public Iterator<String> iterator() {
        final Iterator<HeaderValue> iterator = values.values().iterator();
        return new Iterator<String>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public String next() {
                return iterator.next().getName();
            }

            public void remove() {
                iterator.remove();
            }
        };
    }

    public String getFirst(String headerName) {
        final Deque<String> deque = values.get(headerName.toLowerCase(Locale.US));
        return deque == null ? null : deque.peekFirst();
    }

    public String getLast(String headerName) {
        final Deque<String> deque = values.get(headerName.toLowerCase(Locale.US));
        return deque == null ? null : deque.peekLast();
    }

    public Deque<String> get(String headerName) {
        return values.get(headerName.toLowerCase(Locale.US));
    }

    public void add(String headerName, String headerValue) {
        final String key = headerName.toLowerCase(Locale.US);
        final HeaderValue value = values.get(key);
        if (value == null) {
            values.put(key, new HeaderValue(headerName, headerValue));
        } else {
            value.add(headerValue);
        }
    }

    public void addAll(String headerName, Collection<String> headerValues) {
        final String key = headerName.toLowerCase(Locale.US);
        final HeaderValue value = values.get(key);
        if (value == null) {
            values.put(key, new HeaderValue(headerName, headerValues));
        } else {
            value.addAll(headerValues);
        }
    }

    public void addAll(HeaderMap other) {
        for (Map.Entry<String, HeaderValue> entry : other.values.entrySet()) {
            final String key = entry.getKey();
            final HeaderValue value = entry.getValue();
            final HeaderValue target = values.get(key);
            if (target == null) {
                values.put(key, new HeaderValue(value.getName(), value));
            } else {
                target.addAll(value);
            }
        }
    }

    public Collection<String> getHeaderNames() {
        return new HashSet<String>(values.keySet());
    }

    public void put(String headerName, String headerValue) {
        final String key = headerName.toLowerCase(Locale.US);
        final HeaderValue value = new HeaderValue(headerName, headerValue);
        values.put(key, value);
    }

    public void putAll(String headerName, Collection<String> headerValues) {
        final String key = headerName.toLowerCase(Locale.US);
        final HeaderValue deque = new HeaderValue(headerName, headerValues);
        values.put(key, deque);
    }

    public Collection<String> remove(String headerName) {
        return values.remove(headerName);
    }

    /**
     * Lock this header map to make it immutable.  This method is idempotent.
     */
    public void lock() {

    }

    public boolean contains(String headerName) {
        final HeaderValue value = values.get(headerName.toLowerCase(Locale.US));
        return value != null && ! value.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final HeaderMap strings = (HeaderMap) o;

        if (values != null ? !values.equals(strings.values) : strings.values != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return values != null ? values.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "HeaderMap{" +
                "values=" + values +
                '}';
    }
}
