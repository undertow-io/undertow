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
public final class HeaderMap implements Iterable<HttpString> {

    private final Map<HttpString, ArrayDeque<String>> values = new SecureHashMap<HttpString, ArrayDeque<String>>();

    public Iterator<HttpString> iterator() {
        return values.keySet().iterator();
    }

    public String getFirst(HttpString headerName) {
        final Deque<String> deque = values.get(headerName);
        return deque == null ? null : deque.peekFirst();
    }

    public String getLast(HttpString headerName) {
        final Deque<String> deque = values.get(headerName);
        return deque == null ? null : deque.peekLast();
    }

    public Deque<String> get(HttpString headerName) {
        return values.get(headerName);
    }

    public void add(HttpString headerName, String headerValue) {
        final ArrayDeque<String> value = values.get(headerName);
        if (value == null) {
            values.put(headerName, newHeaderValue(headerValue));
        } else {
            value.add(headerValue);
        }
    }

    private ArrayDeque<String> newHeaderValue(final String value) {
        final ArrayDeque<String> deque = new ArrayDeque<String>();
        deque.add(value);
        return deque;
    }

    private ArrayDeque<String> newHeaderValue(final Collection<String> values) {
        final ArrayDeque<String> deque = new ArrayDeque<String>();
        deque.addAll(values);
        return deque;
    }

    public void addAll(HttpString headerName, Collection<String> headerValues) {
        final ArrayDeque<String> value = values.get(headerName);
        if (value == null) {
            values.put(headerName, newHeaderValue(headerValues));
        } else {
            value.addAll(headerValues);
        }
    }

    public void addAll(HeaderMap other) {
        for (Map.Entry<HttpString, ArrayDeque<String>> entry : other.values.entrySet()) {
            final HttpString key = entry.getKey();
            final ArrayDeque<String> value = entry.getValue();
            final ArrayDeque<String> target = values.get(key);
            if (target == null) {
                values.put(key, newHeaderValue(value));
            } else {
                target.addAll(value);
            }
        }
    }

    public void clear() {
        values.clear();
    }

    public Collection<HttpString> getHeaderNames() {
        return new HashSet<HttpString>(values.keySet());
    }

    public void put(HttpString headerName, String headerValue) {
        final ArrayDeque<String> value = newHeaderValue(headerValue);
        values.put(headerName, value);
    }

    public void putAll(HttpString headerName, Collection<String> headerValues) {
        final ArrayDeque<String> deque = newHeaderValue(headerValues);
        values.put(headerName, deque);
    }

    public Collection<String> remove(HttpString headerName) {
        return values.remove(headerName);
    }

    /**
     * Lock this header map to make it immutable.  This method is idempotent.
     */
    public void lock() {

    }

    public boolean contains(HttpString headerName) {
        final ArrayDeque<String> value = values.get(headerName);
        return value != null && ! value.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "HeaderMap{" +
                "values=" + values +
                '}';
    }
}
