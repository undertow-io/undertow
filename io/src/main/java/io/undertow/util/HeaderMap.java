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

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An optimized array-backed header map.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderMap implements Iterable<HeaderValues> {

    private Object[] table;
    private int size;
    private Collection<HttpString> headerNames;

    public HeaderMap() {
        table = new Object[16];
    }

    private HeaderValues getEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        HeaderValues headerValues;
        if (o instanceof HeaderValues) {
            headerValues = (HeaderValues) o;
            if (! headerName.equals(headerValues.key)) {
                return null;
            }
            return headerValues;
        } else {
            final HeaderValues[] row = (HeaderValues[]) o;
            for (int i = 0; i < row.length; i++) {
                headerValues = row[i];
                if (headerValues != null && headerName.equals(headerValues.key)) {
                    return headerValues;
                }
            }
            return null;
        }
    }


    private HeaderValues getEntry(final String headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = HttpString.hashCodeOf(headerName);
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        HeaderValues headerValues;
        if (o instanceof HeaderValues) {
            headerValues = (HeaderValues) o;
            if (! headerValues.key.equalToString(headerName)) {
                return null;
            }
            return headerValues;
        } else {
            final HeaderValues[] row = (HeaderValues[]) o;
            for (int i = 0; i < row.length; i++) {
                headerValues = row[i];
                if (headerValues != null && headerValues.key.equalToString(headerName)) {
                    return headerValues;
                }
            }
            return null;
        }
    }

    private HeaderValues removeEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final Object[] table = this.table;
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        HeaderValues headerValues;
        if (o instanceof HeaderValues) {
            headerValues = (HeaderValues) o;
            if (! headerName.equals(headerValues.key)) {
                return null;
            }
            table[idx] = null;
            size --;
            return headerValues;
        } else {
            final HeaderValues[] row = (HeaderValues[]) o;
            for (int i = 0; i < row.length; i++) {
                headerValues = row[i];
                if (headerValues != null && headerName.equals(headerValues.key)) {
                    row[i] = null;
                    size --;
                    return headerValues;
                }
            }
            return null;
        }
    }


    private HeaderValues removeEntry(final String headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = HttpString.hashCodeOf(headerName);
        final Object[] table = this.table;
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        HeaderValues headerValues;
        if (o instanceof HeaderValues) {
            headerValues = (HeaderValues) o;
            if (! headerValues.key.equalToString(headerName)) {
                return null;
            }
            table[idx] = null;
            size --;
            return headerValues;
        } else {
            final HeaderValues[] row = (HeaderValues[]) o;
            for (int i = 0; i < row.length; i++) {
                headerValues = row[i];
                if (headerValues != null && headerValues.key.equalToString(headerName)) {
                    row[i] = null;
                    size --;
                    return headerValues;
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
        assert Integer.bitCount(oldLen) == 1;
        Object[] newTable = Arrays.copyOf(table, oldLen << 1);
        table = newTable;
        for (int i = 0; i < oldLen; i ++) {
            if (newTable[i] == null) {
                continue;
            }
            if (newTable[i] instanceof HeaderValues) {
                HeaderValues e = (HeaderValues) newTable[i];
                if ((e.key.hashCode() & oldLen) != 0) {
                    newTable[i] = null;
                    newTable[i + oldLen] = e;
                }
                continue;
            }
            HeaderValues[] oldRow = (HeaderValues[]) newTable[i];
            HeaderValues[] newRow = oldRow.clone();
            int rowLen = oldRow.length;
            newTable[i + oldLen] = newRow;
            HeaderValues item;
            for (int j = 0; j < rowLen; j ++) {
                item = oldRow[j];
                if (item != null) {
                    if ((item.key.hashCode() & oldLen) != 0) {
                        oldRow[j] = null;
                    } else {
                        newRow[j] = null;
                    }
                }
            }
        }
    }

    private HeaderValues getOrCreateEntry(final HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final Object[] table = this.table;
        final int length = table.length;
        final int idx = hc & (length - 1);
        final Object o = table[idx];
        HeaderValues headerValues;
        if (o == null) {
            if (size >= length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            headerValues = new HeaderValues(headerName);
            table[idx] = headerValues;
            size++;
            return headerValues;
        }
        return getOrCreateNonEmpty(headerName, table, length, idx, o);
    }

    private HeaderValues getOrCreateNonEmpty(HttpString headerName, Object[] table, int length, int idx, Object o) {
        HeaderValues headerValues;
        if (o instanceof HeaderValues) {
            headerValues = (HeaderValues) o;
            if (! headerName.equals(headerValues.key)) {
                if (size >= length >> 1) {
                    resize();
                    return getOrCreateEntry(headerName);
                }
                size++;
                final HeaderValues[] row = { headerValues, new HeaderValues(headerName), null, null };
                table[idx] = row;
                return row[1];
            }
            return headerValues;
        } else {
            final HeaderValues[] row = (HeaderValues[]) o;
            int empty = -1;
            for (int i = 0; i < row.length; i++) {
                headerValues = row[i];
                if (headerValues != null) {
                    if (headerName.equals(headerValues.key)) {
                        return headerValues;
                    }
                } else if (empty == -1) {
                    empty = i;
                }
            }
            if (size >= length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            size++;
            headerValues = new HeaderValues(headerName);
            if (empty != -1) {
                row[empty] = headerValues;
            } else {
                if (row.length >= 16) {
                    throw new SecurityException("Excessive collisions");
                }
                final HeaderValues[] newRow = Arrays.copyOf(row, row.length + 3);
                newRow[row.length] = headerValues;
                table[idx] = newRow;
            }
            return headerValues;
        }
    }

    // get

    public HeaderValues get(final HttpString headerName) {
        return getEntry(headerName);
    }

    public HeaderValues get(final String headerName) {
        return getEntry(headerName);
    }

    public String getFirst(HttpString headerName) {
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getFirst();
    }

    public String getFirst(String headerName) {
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getFirst();
    }

    public String get(HttpString headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return null;
        }
        return headerValues.get(index);
    }

    public String get(String headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return null;
        }
        return headerValues.get(index);
    }

    public String getLast(HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getLast();
    }

    public String getLast(String headerName) {
        if (headerName == null) {
            return null;
        }
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getLast();
    }

    // count

    public int count(HttpString headerName) {
        if (headerName == null) {
            return 0;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return 0;
        }
        return headerValues.size();
    }

    public int count(String headerName) {
        if (headerName == null) {
            return 0;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return 0;
        }
        return headerValues.size();
    }

    public int size() {
        return size;
    }

    // iterate

    /**
     * Do a fast iteration of this header map without creating any objects.
     *
     * @return an opaque iterating cookie, or -1 if no iteration is possible
     *
     * @see #fiNext(long)
     * @see #fiCurrent(long)
     */
    public long fastIterate() {
        final Object[] table = this.table;
        final int len = table.length;
        int ri = 0;
        int ci;
        while (ri < len) {
            final Object item = table[ri];
            if (item != null) {
                if (item instanceof HeaderValues) {
                    return (long)ri << 32L;
                } else {
                    final HeaderValues[] row = (HeaderValues[]) item;
                    ci = 0;
                    final int rowLen = row.length;
                    while (ci < rowLen) {
                        if (row[ci] != null) {
                            return (long)ri << 32L | (ci & 0xffffffffL);
                        }
                        ci ++;
                    }
                }
            }
            ri++;
        }
        return -1L;
    }

    /**
     * Do a fast iteration of this header map without creating any objects, only considering non-empty header values.
     *
     * @return an opaque iterating cookie, or -1 if no iteration is possible
     */
    public long fastIterateNonEmpty() {
        final Object[] table = this.table;
        final int len = table.length;
        int ri = 0;
        int ci;
        while (ri < len) {
            final Object item = table[ri];
            if (item != null) {
                if (item instanceof HeaderValues) {
                    if(!((HeaderValues) item).isEmpty()) {
                        return (long) ri << 32L;
                    }
                } else {
                    final HeaderValues[] row = (HeaderValues[]) item;
                    ci = 0;
                    final int rowLen = row.length;
                    while (ci < rowLen) {
                        if (row[ci] != null && !row[ci].isEmpty()) {
                            return (long)ri << 32L | (ci & 0xffffffffL);
                        }
                        ci ++;
                    }
                }
            }
            ri++;
        }
        return -1L;
    }

    /**
     * Find the next index in a fast iteration.
     *
     * @param cookie the previous cookie value
     * @return the next cookie value, or -1L if iteration is done
     */
    public long fiNext(long cookie) {
        if (cookie == -1L) return -1L;
        final Object[] table = this.table;
        final int len = table.length;
        int ri = (int) (cookie >> 32);
        int ci = (int) cookie;
        Object item = table[ri];
        if (item instanceof HeaderValues[]) {
            final HeaderValues[] row = (HeaderValues[]) item;
            final int rowLen = row.length;
            if (++ci >= rowLen) {
                ri ++; ci = 0;
            } else if (row[ci] != null) {
                return (long)ri << 32L | (ci & 0xffffffffL);
            }
        } else {
            ri ++; ci = 0;
        }
        while (ri < len) {
            item = table[ri];
            if (item instanceof HeaderValues) {
                return (long)ri << 32L;
            } else if (item instanceof HeaderValues[]) {
                final HeaderValues[] row = (HeaderValues[]) item;
                final int rowLen = row.length;
                while (ci < rowLen) {
                    if (row[ci] != null) {
                        return (long)ri << 32L | (ci & 0xffffffffL);
                    }
                    ci ++;
                }
            }
            ci = 0;
            ri ++;
        }
        return -1L;
    }

    /**
     * Find the next non-empty index in a fast iteration.
     *
     * @param cookie the previous cookie value
     * @return the next cookie value, or -1L if iteration is done
     */
    public long fiNextNonEmpty(long cookie) {
        if (cookie == -1L) return -1L;
        final Object[] table = this.table;
        final int len = table.length;
        int ri = (int) (cookie >> 32);
        int ci = (int) cookie;
        Object item = table[ri];
        if (item instanceof HeaderValues[]) {
            final HeaderValues[] row = (HeaderValues[]) item;
            final int rowLen = row.length;
            if (++ci >= rowLen) {
                ri ++; ci = 0;
            } else if (row[ci] != null && !row[ci].isEmpty()) {
                return (long)ri << 32L | (ci & 0xffffffffL);
            }
        } else {
            ri ++; ci = 0;
        }
        while (ri < len) {
            item = table[ri];
            if (item instanceof HeaderValues && !((HeaderValues) item).isEmpty()) {
                return (long)ri << 32L;
            } else if (item instanceof HeaderValues[]) {
                final HeaderValues[] row = (HeaderValues[]) item;
                final int rowLen = row.length;
                while (ci < rowLen) {
                    if (row[ci] != null && !row[ci].isEmpty()) {
                        return (long)ri << 32L | (ci & 0xffffffffL);
                    }
                    ci ++;
                }
            }
            ci = 0;
            ri ++;
        }
        return -1L;
    }

    /**
     * Return the value at the current index in a fast iteration.
     *
     * @param cookie the iteration cookie value
     * @return the values object at this position
     * @throws NoSuchElementException if the cookie value is invalid
     */
    public HeaderValues fiCurrent(long cookie) {
        try {
            final Object[] table = this.table;
            int ri = (int) (cookie >> 32);
            int ci = (int) cookie;
            final Object item = table[ri];
            if (item instanceof HeaderValues[]) {
                return ((HeaderValues[])item)[ci];
            } else if (ci == 0) {
                return (HeaderValues) item;
            } else {
                throw new NoSuchElementException();
            }
        } catch (RuntimeException e) {
            throw new NoSuchElementException();
        }
    }

    public Iterable<String> eachValue(final HttpString headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final HeaderValues entry = getEntry(headerName);
        if (entry == null) {
            return Collections.emptyList();
        }
        return entry;
    }

    public Iterator<HeaderValues> iterator() {
        return new Iterator<HeaderValues>() {
            final Object[] table = HeaderMap.this.table;
            boolean consumed;
            int ri, ci;

            private HeaderValues _next() {
                for (;;) {
                    if (ri >= table.length) {
                        return null;
                    }
                    final Object o = table[ri];
                    if (o == null) {
                        // zero-entry row
                        ri++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    if (o instanceof HeaderValues) {
                        // one-entry row
                        if (ci > 0 || consumed) {
                            ri++;
                            ci = 0;
                            consumed = false;
                            continue;
                        }
                        return (HeaderValues) o;
                    }
                    final HeaderValues[] row = (HeaderValues[]) o;
                    final int len = row.length;
                    if (ci >= len) {
                        ri ++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    if (consumed) {
                        ci++;
                        consumed = false;
                        continue;
                    }
                    final HeaderValues headerValues = row[ci];
                    if (headerValues == null) {
                        ci ++;
                        continue;
                    }
                    return headerValues;
                }
            }

            public boolean hasNext() {
                return _next() != null;
            }

            public HeaderValues next() {
                final HeaderValues next = _next();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                consumed = true;
                return next;
            }

            public void remove() {
            }
        };
    }

    public Collection<HttpString> getHeaderNames() {
        if (headerNames != null) {
            return headerNames;
        }
        return headerNames = new AbstractCollection<HttpString>() {
            public boolean contains(final Object o) {
                return o instanceof HttpString && getEntry((HttpString) o) != null;
            }

            public boolean add(final HttpString httpString) {
                getOrCreateEntry(httpString);
                return true;
            }

            public boolean remove(final Object o) {
                if (! (o instanceof HttpString)) return false;
                HttpString s = (HttpString) o;
                HeaderValues entry = getEntry(s);
                if (entry == null) {
                    return false;
                }
                entry.clear();
                return true;
            }

            public void clear() {
                HeaderMap.this.clear();
            }

            public Iterator<HttpString> iterator() {
                final Iterator<HeaderValues> iterator = HeaderMap.this.iterator();
                return new Iterator<HttpString>() {
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    public HttpString next() {
                        return iterator.next().getHeaderName();
                    }

                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            public int size() {
                return HeaderMap.this.size();
            }
        };
    }

    // add

    public HeaderMap add(HttpString headerName, String headerValue) {
        addLast(headerName, headerValue);
        return this;
    }

    public HeaderMap addFirst(final HttpString headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return this;
        }
        getOrCreateEntry(headerName).addFirst(headerValue);
        return this;
    }

    public HeaderMap addLast(final HttpString headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return this;
        }
        getOrCreateEntry(headerName).addLast(headerValue);
        return this;
    }

    public HeaderMap add(HttpString headerName, long headerValue) {
        add(headerName, Long.toString(headerValue));
        return this;
    }


    public HeaderMap addAll(HttpString headerName, Collection<String> headerValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValues == null || headerValues.isEmpty()) {
            return this;
        }
        getOrCreateEntry(headerName).addAll(headerValues);
        return this;
    }

    // put

    public HeaderMap put(HttpString headerName, String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            remove(headerName);
            return this;
        }
        final HeaderValues headerValues = getOrCreateEntry(headerName);
        headerValues.clear();
        headerValues.add(headerValue);
        return this;
    }

    public HeaderMap put(HttpString headerName, long headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        final HeaderValues entry = getOrCreateEntry(headerName);
        entry.clear();
        entry.add(Long.toString(headerValue));
        return this;
    }

    public HeaderMap putAll(HttpString headerName, Collection<String> headerValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValues == null || headerValues.isEmpty()) {
            remove(headerName);
            return this;
        }
        final HeaderValues entry = getOrCreateEntry(headerName);
        entry.clear();
        entry.addAll(headerValues);
        return this;
    }

    // clear

    public HeaderMap clear() {
        Arrays.fill(table, null);
        size = 0;
        return this;
    }

    // remove

    public Collection<String> remove(HttpString headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final Collection<String> values = removeEntry(headerName);
        return values != null ? values : Collections.<String>emptyList();
    }

    public Collection<String> remove(String headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final Collection<String> values = removeEntry(headerName);
        return values != null ? values : Collections.<String>emptyList();
    }

    // contains

    public boolean contains(HttpString headerName) {
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return false;
        }
        final Object v = headerValues.value;
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

    public boolean contains(String headerName) {
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return false;
        }
        final Object v = headerValues.value;
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

    // compare

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
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for(HttpString name : getHeaderNames()) {
            if(first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
            sb.append("=[");
            boolean f = true;
            for(String val : get(name)) {
                if(f) {
                    f = false;
                } else {
                    sb.append(", ");
                }
                sb.append(val);
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }
}
