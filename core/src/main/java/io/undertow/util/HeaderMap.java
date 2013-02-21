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

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This implementation sucks and is incomplete.  It's just here to illustrate.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderMap implements Iterable<HttpString> {

    private Object[] table;
    private int size;

    public HeaderMap() {
        clear();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private Entry getEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        Entry entry;
        if (o instanceof Entry) {
            entry = (Entry) o;
            if (! headerName.equals(entry.key)) {
                return null;
            }
            return entry;
        } else {
            final Entry[] row = (Entry[]) o;
            for (int i = 0; i < row.length; i++) {
                entry = row[i];
                if (entry != null && headerName.equals(entry.key)) { return entry; }
            }
            return null;
        }
    }

    private Entry removeEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        Entry entry;
        if (o instanceof Entry) {
            entry = (Entry) o;
            if (! headerName.equals(entry.key)) {
                return null;
            }
            table[idx] = null;
            size --;
            return entry;
        } else {
            final Entry[] row = (Entry[]) o;
            for (int i = 0; i < row.length; i++) {
                entry = row[i];
                if (entry != null && headerName.equals(entry.key)) {
                    row[i] = null;
                    size --;
                    return entry;
                }
            }
            return null;
        }
    }

    private void resize() {
        final int oldLen = table.length;
        if (oldLen == 0x40000000) {
            return;
        }
        Object[] newTable = Arrays.copyOf(table, oldLen << 1);
        table = newTable;
        for (int i = 0; i < oldLen; i ++) {
            if (newTable[i] == null) {
                continue;
            }
            if (newTable[i] instanceof Entry) {
                Entry e = (Entry) newTable[i];
                if ((e.key.hashCode() & oldLen) != 0) {
                    newTable[i] = null;
                    newTable[i + oldLen] = e;
                }
                continue;
            }
            Entry[] oldRow = (Entry[]) newTable[i];
            Entry[] newRow = oldRow.clone();
            int rowLen = oldRow.length;
            newTable[i + oldLen] = newRow;
            for (int j = 0; j < rowLen; j ++) {
                if ((oldRow[j].key.hashCode() & oldLen) != 0) {
                    oldRow[j] = null;
                } else {
                    newRow[j] = null;
                }
            }
        }
    }

    private Entry getOrCreateEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        Entry entry;
        if (o == null) {
            if (size >= table.length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            entry = new Entry(headerName);
            table[idx] = entry;
            size++;
            return entry;
        }
        if (o instanceof Entry) {
            entry = (Entry) o;
            if (! headerName.equals(entry.key)) {
                if (size >= table.length >> 1) {
                    resize();
                    return getOrCreateEntry(headerName);
                }
                size++;
                final Entry[] row = { entry, new Entry(headerName), null, null };
                table[idx] = row;
                return row[1];
            }
            return entry;
        } else {
            final Entry[] row = (Entry[]) o;
            int empty = -1;
            for (int i = 0; i < row.length; i++) {
                entry = row[i];
                if (entry == null) {
                    empty = i;
                } else {
                    if (headerName.equals(entry.key)) { return entry; }
                }
            }
            if (size >= table.length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            size++;
            entry = new Entry(headerName);
            if (empty != -1) {
                row[empty] = entry;
            } else {
                final Entry[] newRow = Arrays.copyOf(row, row.length + 3);
                newRow[row.length] = entry;
                table[idx] = newRow;
            }
            return entry;
        }
    }

    public String getFirst(HttpString headerName) {
        Entry entry = getEntry(headerName);
        if (entry == null) return null;
        final Object v = entry.value;
        if (v instanceof String) {
            return (String) v;
        }
        final String[] list = (String[]) v;
        String s;
        for (int i = 0; i < list.length; i++) {
            s = list[i];
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public String get(HttpString headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final Entry entry = getEntry(headerName);
        if (entry == null) {
            return null;
        }
        final Object v = entry.value;
        if (v instanceof String) {
            if (index == 0) {
                return (String) v;
            }
            throw new IndexOutOfBoundsException();
        }
        final String[] list = (String[]) v;
        String s;
        for (int i = 0; i < list.length; i++) {
            s = list[i];
            if (s != null) {
                if (index-- == 0) {
                    return s;
                }
            }
        }
        throw new IndexOutOfBoundsException();
    }

    public String getLast(HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        Entry entry = getEntry(headerName);
        if (entry == null) return null;
        final Object v = entry.value;
        if (v instanceof String) {
            return (String) v;
        }
        final String[] list = (String[]) v;
        String s;
        for (int i = list.length - 1; i >= 0; i--) {
            s = list[i];
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public int count(HttpString headerName) {
        if (headerName == null) {
            return 0;
        }
        final Entry entry = getEntry(headerName);
        if (entry == null) {
            return 0;
        }
        final Object v = entry.value;
        if (v instanceof String) {
            return 1;
        }
        final String[] list = (String[]) v;
        String s;
        int cnt = 0;
        for (int i = 0; i < list.length; i++) {
            s = list[i];
            if (s != null) {
                cnt++;
            }
        }
        return cnt;
    }

    public List<String> get(final HttpString headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        return new AbstractList<String>() {
            public String get(int index) {
                return HeaderMap.this.get(headerName, index);
            }

            public int size() {
                return count(headerName);
            }

            public void clear() {
                remove(headerName);
            }

            public boolean add(final String s) {
                HeaderMap.this.add(headerName, s);
                return true;
            }
        };
    }

    public void add(HttpString headerName, String headerValue) {
        addLast(headerName, headerValue);
    }

    public void addFirst(final HttpString headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return;
        }
        final Entry entry = getOrCreateEntry(headerName);
        final Object v = entry.value;
        if (v == null) {
            entry.value = headerValue;
            return;
        }
        if (v instanceof String) {
            entry.value = new String[] { headerValue, (String) v, null, null };
            return;
        }
        final String[] list = (String[]) v;
        String s;
        int empty = -1;
        for (int i = 0; i < list.length; i++) {
            s = list[i];
            if (s == null) {
                empty = i;
            } else {
                if (empty != -1) {
                    list[empty] = headerValue;
                    return;
                }
                break;
            }
        }
        if (empty != -1) {
            list[empty] = headerValue;
            return;
        }
        final String[] newList = new String[list.length + 3];
        System.arraycopy(list, 0, newList, 3, list.length);
        newList[2] = headerValue;
    }

    public void addLast(final HttpString headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return;
        }
        final Entry entry = getOrCreateEntry(headerName);
        final Object v = entry.value;
        if (v == null) {
            entry.value = headerValue;
            return;
        }
        if (v instanceof String) {
            entry.value = new String[] { (String) v, headerValue, null, null };
            return;
        }
        final String[] list = (String[]) v;
        String s;
        int empty = -1;
        for (int i = list.length - 1; i >= 0; i--) {
            s = list[i];
            if (s == null) {
                empty = i;
            } else {
                if (empty != -1) {
                    list[empty] = headerValue;
                    return;
                }
                break;
            }
        }
        if (empty != -1) {
            list[empty] = headerValue;
            return;
        }
        final String[] newList = Arrays.copyOf(list, list.length + 3);
        newList[list.length] = headerValue;
    }

    public void add(HttpString headerName, long headerValue) {
        addLast(headerName, Long.toString(headerValue));
    }

    public void addAll(HttpString headerName, Collection<String> headerValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValues == null || headerValues.isEmpty()) {
            return;
        }
        final int valuesSize = headerValues.size();
        if (valuesSize == 1) {
            addLast(headerName, headerValues.iterator().next());
            return;
        }
        final Entry entry = getOrCreateEntry(headerName);
        final Object v = entry.value;
        if (v == null) {
            entry.value = headerValues.toArray(new String[valuesSize]);
            return;
        }
        if (v instanceof String) {
            final String[] newList = new String[valuesSize + 4];
            newList[0] = (String) v;
            int i = 1;
            for (String value : headerValues) {
                newList[i++] = value;
            }
            entry.value = newList;
            return;
        }
        final String[] list = (String[]) v;
        String s;
        int empty = -1;
        for (int i = list.length - 1; i >= 0; i--) {
            s = list[i];
            if (s == null) {
                empty = i;
            } else {
                if (empty != -1) {
                    final String[] newList = Arrays.copyOfRange(list, 0, empty + valuesSize);
                    for (String value : headerValues) {
                        newList[empty++] = value;
                    }
                    entry.value = newList;
                    return;
                }
                break;
            }
        }
        if (empty != -1) {
            entry.value = headerValues.toArray(new String[valuesSize]);
        } else {
            final String[] newList = Arrays.copyOfRange(list, 0, list.length + valuesSize);
            int i = list.length;
            for (String value : headerValues) {
                newList[i++] = value;
            }
            entry.value = newList;
        }
    }

    public void clear() {
        table = new Object[16];
        size = 0;
    }

    public Collection<HttpString> getHeaderNames() {
        return new AbstractCollection<HttpString>() {
            public Iterator<HttpString> iterator() {
                return HeaderMap.this.iterator();
            }

            public int size() {
                return size;
            }
        };
    }

    public void put(HttpString headerName, String headerValue) {
        final Entry entry = getOrCreateEntry(headerName);
        entry.value = headerValue;
    }

    public void put(HttpString headerName, long headerValue) {
        put(headerName, Long.toString(headerValue));
    }

    public void putAll(HttpString headerName, Collection<String> headerValues) {
        final Entry entry = getOrCreateEntry(headerName);
        entry.value = headerValues.toArray(new String[headerValues.size()]);
    }

    public Collection<String> remove(HttpString headerName) {
        final Entry entry = removeEntry(headerName);
        final Object value = entry.value;
        if (value == null) {
            return Collections.emptyList();
        } else if (value instanceof String) {
            return Collections.singletonList((String) value);
        } else {
            final String[] list = (String[]) value;
            final ArrayList<String> arrayList = new ArrayList<>(list.length);
            for (String s : list) {
                if (s != null) {
                    arrayList.add(s);
                }
            }
            return arrayList;
        }
    }

    /**
     * Lock this header map to make it immutable.  This method is idempotent.
     */
    public void lock() {

    }

    public boolean contains(HttpString headerName) {
        final Entry entry = getEntry(headerName);
        if (entry == null) {
            return false;
        }
        final Object v = entry.value;
        if (v instanceof String) {
            return true;
        }
        final String[] list = (String[]) v;
        for (int i = 0; i < list.length; i++) {
            if (list[i] != null) {
                return true;
            }
        }
        return false;
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
        // todo...
        return "HeaderMap";
    }

    public Iterator<HttpString> iterator() {
        return new Iterator<HttpString>() {
            final Object[] table = HeaderMap.this.table;
            boolean consumed;
            int ri, ci;

            private Entry _next() {
                for (;;) {
                    if (ri >= table.length) {
                        return null;
                    }
                    final Object o = table[ri];
                    if (o == null) {
                        ri++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    if (o instanceof Entry) {
                        if (ci > 0 || consumed) {
                            ri++;
                            ci = 0;
                            consumed = false;
                            continue;
                        }
                        return (Entry) o;
                    }
                    final Entry[] row = (Entry[]) o;
                    final int len = row.length;
                    if (ci >= len || consumed) {
                        ri ++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    final Entry entry = row[ci++];
                    if (entry == null) {
                        continue;
                    }
                    return entry;
                }
            }

            public boolean hasNext() {
                return _next() != null;
            }

            public HttpString next() {
                final Entry next = _next();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                consumed = true;
                return next.key;
            }

            public void remove() {
            }
        };
    }

    static class Entry {
        final HttpString key;
        Object value;

        Entry(final HttpString key) {
            this.key = key;
        }
    }
}
