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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This implementation sucks and is incomplete.  It's just here to illustrate.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderMap implements Iterable<HttpString> {

    private static final int SIZE = 17;

    private static final int SIGN_MASK = 0x7FFFFFFF;

    private final HeaderEntry[] entries = new HeaderEntry[SIZE];

    public Iterator<HttpString> iterator() {
        return new MapIterator();
    }

    public String getFirst(HttpString headerName) {
        Object value = getValue(headerName);
        if (value instanceof List) {
            return ((List<String>) value).get(0);
        } else {
            return (String) value;
        }
    }


    public String getLast(HttpString headerName) {
        Object value = getValue(headerName);
        if (value instanceof List) {
            List<String> list = (List<String>) value;
            return list.get(list.size() - 1);
        } else {
            return (String) value;
        }
    }

    public List<String> get(HttpString headerName) {
        Object value = getValue(headerName);
        if (value == null) {
            return null;
        } else if (value instanceof List) {
            return (List<String>) value;
        } else {
            return Collections.singletonList((String) value);
        }
    }

    public void add(HttpString headerName, String headerValue) {
        HeaderEntry entry = getEntry(headerName);
        if (entry == null) {
            final int pos = (headerName.hashCode() & SIGN_MASK) % SIZE;
            HeaderEntry exiting = entries[pos];
            entry = new HeaderEntry();
            entry.next = exiting;
            entry.name = headerName;
            entry.value = headerValue;
            entries[pos] = entry;
        } else {
            if (entry.value instanceof List) {
                ((List) entry.value).add(headerValue);
            } else {
                final ArrayList<String> list = new ArrayList<String>(1);
                list.add((String) entry.value);
                list.add(headerValue);
                entry.value = list;
            }
        }
    }

    public void add(HttpString headerName, long headerValue) {
        add(headerName, Long.toString(headerValue));
    }


    public void addAll(HttpString headerName, Collection<String> headerValues) {
        HeaderEntry entry = getEntry(headerName);
        if (entry == null) {
            final int pos = (headerName.hashCode() & SIGN_MASK) % SIZE;
            HeaderEntry exiting = entries[pos];
            entry = new HeaderEntry();
            entry.next = exiting;
            entry.name = headerName;
            entry.value = new ArrayList<>(headerValues);
            entries[pos] = entry;
        } else {
            if (entry.value instanceof List) {
                ((List) entry.value).addAll(headerValues);
            } else {
                final ArrayList<String> list = new ArrayList<String>(1);
                list.add((String) entry.value);
                list.addAll(headerValues);
                entry.value = list;
            }
        }
    }

    public void clear() {
        for(int i = 0; i < SIZE; ++i) {
            entries[i] = null;
        }
    }

    public Collection<HttpString> getHeaderNames() {

        HashSet<HttpString> ret = new HashSet<>();
        for(HttpString i : this) {
            ret.add(i);
        }
        return ret;
    }

    public void put(HttpString headerName, String headerValue) {
        HeaderEntry entry = getEntry(headerName);
        if (entry == null) {
            final int pos = (headerName.hashCode() & SIGN_MASK) % SIZE;
            HeaderEntry exiting = entries[pos];
            entry = new HeaderEntry();
            entry.next = exiting;
            entry.name = headerName;
            entry.value = headerValue;
            entries[pos] = entry;
        } else {
            entry.value = headerValue;
        }
    }

    public void put(HttpString headerName, long headerValue) {
        put(headerName, Long.toString(headerValue));
    }

    public Collection<String> remove(HttpString headerName) {
        final int pos = (headerName.hashCode() & SIGN_MASK) % SIZE;
        HeaderEntry entry = entries[pos];
        if(entry == null) {
            return null;
        }
        if(entry.name.equals(headerName)) {
            entries[pos] = entry.next;
            if (entry.value instanceof List) {
                return (Collection<String>) entry.value;
            } else {
                return (List)Collections.singletonList(entry.value);
            }
        }
        HeaderEntry prev = entry;
        entry = entry.next;
        while (entry != null) {
            if(entry.name.equals(headerName)) {
                prev.next = entry.next;
                if (entry.value instanceof List) {
                    return (Collection<String>) entry.value;
                } else {
                    return (List)Collections.singletonList(entry.value);
                }
            }
            prev = entry;
            entry = entry.next;
        }
        return null;
    }

    /**
     * Lock this header map to make it immutable.  This method is idempotent.
     */
    public void lock() {

    }

    public boolean contains(HttpString headerName) {
        final Object value = getEntry(headerName);
        return value != null;
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
                "values=" + entries +
                '}';
    }

    private Object getValue(HttpString headerName) {
        HeaderEntry entry = getEntry(headerName);
        if(entry == null) {
            return null;
        }
        return entry.value;
    }

    private HeaderEntry getEntry(HttpString headerName) {
        final int pos = (headerName.hashCode() & SIGN_MASK) % SIZE;
        HeaderEntry entry = entries[pos];
        while (entry != null) {
            if(entry.name.equals(headerName)) {
                return entry;
            }
            entry = entry.next;
        }
        return null;
    }

    private static final class HeaderEntry {
        HeaderEntry next;
        Object value;
        HttpString name;
    }

    private class MapIterator implements Iterator<HttpString> {
        private int pos = 0;
        private HeaderEntry current;

        MapIterator() {
            while (pos < entries.length && entries[pos] == null) {
                ++pos;
            }
            if (pos < entries.length) {
                current = entries[pos];
            }
        }


        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public HttpString next() {
            HttpString toReturn = current.name;
            if (current.next != null) {
                current = current.next;
            } else {

                do {
                    ++pos;
                } while (pos < entries.length && entries[pos] == null);

                if (pos < entries.length) {
                    current = entries[pos];
                } else {
                    current = null;
                }
            }
            return toReturn;
        }

        @Override
        public void remove() {
            throw new IllegalStateException();
        }
    }
}
