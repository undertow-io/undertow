/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/**
 * An array-backed list/deque for header string values.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderValues extends AbstractCollection<String> implements Deque<String>, List<String>, RandomAccess {

    private static final String[] NO_STRINGS = new String[0];
    final HttpString key;
    byte head, size;
    Object value;

    HeaderValues(final HttpString key) {
        this.key = key;
    }

    public HttpString getHeaderName() {
        return key;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        final byte size = this.size;
        if (size == 0) return;
        clearInternal(size);
    }

    private void clearInternal(byte size) {
        final byte head = this.head;
        final Object value = this.value;
        if (value instanceof String[]) {
            final String[] strings = (String[]) value;
            final int len = strings.length;
            final int tail = head + size;
            if (tail > len) {
                Arrays.fill(strings, head, len, null);
                Arrays.fill(strings, 0, tail - len, null);
            } else {
                Arrays.fill(strings, head, tail, null);
            }
        } else {
            this.value = null;
        }
        this.head = this.size = 0;
    }

    private int index(int idx) {
        assert idx >= 0;
        assert idx < size;
        idx += head;
        final int len = ((String[]) value).length;
        if (idx > len) {
            idx -= len;
        }
        return idx;
    }

    public ListIterator<String> listIterator() {
        return iterator(0, true);
    }

    public ListIterator<String> listIterator(final int index) {
        return iterator(index, true);
    }

    public Iterator<String> iterator() {
        return iterator(0, true);
    }

    public Iterator<String> descendingIterator() {
        return iterator(0, false);
    }

    private ListIterator<String> iterator(final int start, final boolean forwards) {
        return new ListIterator<String>() {
            int idx = start;
            int returned = -1;

            public boolean hasNext() {
                return idx < size;
            }

            public boolean hasPrevious() {
                return idx > 0;
            }

            public String next() {
                try {
                    final String next;
                    if (forwards) {
                        int idx = this.idx;
                        next = get(idx);
                        returned = idx;
                        this.idx = idx + 1;
                        return next;
                    } else {
                        int idx = this.idx - 1;
                        next = get(idx);
                        this.idx = returned = idx;
                    }
                    return next;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            public int nextIndex() {
                return idx;
            }

            public String previous() {
                try {
                    final String prev;
                    if (forwards) {
                        int idx = this.idx - 1;
                        prev = get(idx);
                        this.idx = returned = idx;
                    } else {
                        int idx = this.idx;
                        prev = get(idx);
                        returned = idx;
                        this.idx = idx + 1;
                        return prev;
                    }
                    return prev;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            public int previousIndex() {
                return idx - 1;
            }

            public void remove() {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                HeaderValues.this.remove(returned);
                returned = -1;
            }

            public void set(final String headerValue) {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                HeaderValues.this.set(returned, headerValue);
            }

            public void add(final String headerValue) {
                if (returned == -1) {
                    throw new IllegalStateException();
                }
                final int idx = this.idx;
                HeaderValues.this.add(idx, headerValue);
                this.idx = idx + 1;
                returned = -1;
            }
        };
    }

    public boolean offerFirst(final String headerValue) {
        int size = this.size;
        if (headerValue == null || size == Byte.MAX_VALUE) return false;
        final Object value = this.value;
        if (value instanceof String[]) {
            final String[] strings = (String[]) value;
            final int len = strings.length;
            final byte head = this.head;
            if (size == len) {
                final String[] newStrings = Arrays.copyOfRange(strings, head, head + len + (len << 1));
                final int end = head + size;
                if (end > len) {
                    System.arraycopy(strings, 0, newStrings, len - head, end - len);
                }
                newStrings[this.head = (byte) (head - 1)] = headerValue;
                this.value = newStrings;
            } else if (head == 0) {
                strings[this.head = (byte) (len - 1)] = headerValue;
            } else {
                strings[this.head = (byte) (head - 1)] = headerValue;
            }
            this.size = (byte) (size + 1);
        } else {
            if (size == 0) {
                this.value = headerValue;
                this.size = (byte) 1;
            } else {
                this.value = new String[] { headerValue, (String) value, null, null };
                this.size = (byte) 2;
            }
            this.head = 0;
        }
        return true;
    }

    public boolean offerLast(final String headerValue) {
        int size = this.size;
        if (headerValue == null || size == Byte.MAX_VALUE) return false;
        final Object value = this.value;
        if (value instanceof String[]) {
            offerLastMultiValue(headerValue, size, (String[]) value);
        } else {
            if (size == 0) {
                this.value = headerValue;
                this.size = (byte) 1;
            } else {
                this.value = new String[] { (String) value, headerValue, null, null };
                this.size = (byte) 2;
            }
            this.head = 0;
        }
        return true;
    }

    private void offerLastMultiValue(String headerValue, int size, String[] value) {
        final String[] strings = (String[]) value;
        final int len = strings.length;
        final byte head = this.head;
        final int end = head + size;
        if (size == len) {
            final String[] newStrings = Arrays.copyOfRange(strings, head, head + len + (len << 1));
            if (end > len) {
                System.arraycopy(strings, 0, newStrings, len - head, end - len);
            }
            newStrings[len] = headerValue;
            this.value = newStrings;
        } else if (end >= len) {
            strings[end - len] = headerValue;
        } else {
            strings[end] = headerValue;
        }
        this.size = (byte) (size + 1);
    }

    private boolean offer(int idx, final String headerValue) {
        int size = this.size;
        if (idx < 0 || idx > size || size == Byte.MAX_VALUE || headerValue == null) return false;
        if (idx == 0) return offerFirst(headerValue);
        if (idx == size) return offerLast(headerValue);
        assert size >= 2; // must be >= 2 to pass the last two checks
        final Object value = this.value;
        assert value instanceof String[];
        final String[] strings = (String[]) value;
        final int len = strings.length;
        final byte head = this.head;
        final int end = head + size;
        final int headIdx = head + idx;
        // This stuff is all algebraically derived.
        if (size == len) {
            // Grow the list, copy each segment into new spots so that head = 0
            final int newLen = (len << 1) + len;
            final String[] newStrings = new String[newLen];
            if (head == 0) {
                assert headIdx == len;
                assert end == len;
                System.arraycopy(value, 0, newStrings, 0, idx);
                System.arraycopy(value, idx, newStrings, idx + 1, len - idx);
            } else if (headIdx < len) {
                System.arraycopy(value, head, newStrings, 0, idx);
                System.arraycopy(value, headIdx, newStrings, idx + 1, len - headIdx);
                System.arraycopy(value, 0, newStrings, len - head + 1, head);
            } else if (headIdx > len) {
                System.arraycopy(value, 0, newStrings, len - head, headIdx - len);
                System.arraycopy(value, headIdx - len, newStrings, idx + 1, len - idx + 1);
                System.arraycopy(value, head, newStrings, 0, len - head);
            }
            // finally fill in the new value
            newStrings[idx] = headerValue;
            this.value = newStrings;
            this.head = 0;
        } else if (end > len) {
            if (headIdx < len) {
                System.arraycopy(value, head, value, head - 1, idx);
                strings[headIdx - 1] = headerValue;
                this.head = (byte) (head - 1);
            } else if (headIdx > len) {
                System.arraycopy(value, headIdx - len, value, headIdx - len + 1, size - idx);
                strings[headIdx - len] = headerValue;
            } else {
                assert headIdx == len;
                System.arraycopy(value, 0, value, 1, end - len);
                strings[0] = headerValue;
            }
            strings[idx] = headerValue;
        } else {
            assert size < len && end <= len;
            if (head == 0 || idx >= size >> 1) {
                assert end < len;
                System.arraycopy(value, headIdx, value, headIdx + 1, size - idx);
                strings[headIdx] = headerValue;
            } else {
                assert end <= len || idx < size << 1;
                assert head > 0;
                System.arraycopy(value, headIdx, value, headIdx - 1, size - idx);
                strings[headIdx - 1] = headerValue;
                this.head = (byte) (head - 1);
            }
        }
        this.size = (byte) (size + 1);
        return true;
    }

    public String pollFirst() {
        final byte size = this.size;
        if (size == 0) return null;

        final Object value = this.value;
        if (value instanceof String) {
            this.size = 0;
            this.value = null;
            return (String) value;
        } else {
            final String[] strings = (String[]) value;
            int idx = head++;
            this.size = (byte) (size - 1);
            final int len = strings.length;
            if (idx > len) idx -= len;
            try {
                return strings[idx];
            } finally {
                strings[idx] = null;
            }
        }
    }

    public String pollLast() {
        final byte size = this.size;
        if (size == 0) return null;

        final Object value = this.value;
        if (value instanceof String) {
            this.size = 0;
            this.value = null;
            return (String) value;
        } else {
            final String[] strings = (String[]) value;
            int idx = head + (this.size = (byte) (size - 1));
            final int len = strings.length;
            if (idx > len) idx -= len;
            try {
                return strings[idx];
            } finally {
                strings[idx] = null;
            }
        }
    }

    public String remove(int idx) {
        final int size = this.size;
        if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException();
        if (idx == 0) return removeFirst();
        if (idx == size - 1) return removeLast();
        assert size > 2; // must be > 2 to pass the last two checks
        // value must be an array since size > 2
        final String[] value = (String[]) this.value;
        final int len = value.length;
        final byte head = this.head;
        final int headIdx = idx + head;
        final int end = head + size;
        if (end > len) {
            if (headIdx > len) {
                try {
                    return value[headIdx - len];
                } finally {
                    System.arraycopy(value, headIdx + 1 - len, value, headIdx - len, size - idx - 1);
                    this.size = (byte) (size - 1);
                }
            } else {
                try {
                    return value[headIdx];
                } finally {
                    System.arraycopy(value, head, value, head + 1, idx);
                    this.size = (byte) (size - 1);
                }
            }
        } else {
            try {
                return value[headIdx];
            } finally {
                System.arraycopy(value, headIdx + 1, value, headIdx, size - idx - 1);
                this.size = (byte) (size - 1);
            }
        }
    }

    public String get(int idx) {
        if (idx > size) {
            throw new IndexOutOfBoundsException();
        }
        Object value = this.value;
        assert value != null;
        if (value instanceof String) {
            assert size == 1;
            return (String) value;
        }
        final String[] a = (String[]) value;
        return a[index(idx)];
    }

    public int indexOf(final Object o) {
        if (o == null || size == 0) return -1;
        if (value instanceof String[]) {
            final String[] list = (String[]) value;
            final int len = list.length;
            int idx;
            for (int i = 0; i < size; i ++) {
                idx = i + head;
                if ((idx > len ? list[idx - len] : list[idx]).equals(o)) {
                    return i;
                }
            }
        } else if (o.equals(value)) {
            return 0;
        }
        return -1;
    }

    public int lastIndexOf(final Object o) {
        if (o == null || size == 0) return -1;
        if (value instanceof String[]) {
            final String[] list = (String[]) value;
            final int len = list.length;
            int idx;
            for (int i = size - 1; i >= 0; i --) {
                idx = i + head;
                if ((idx > len ? list[idx - len] : list[idx]).equals(o)) {
                    return i;
                }
            }
        } else if (o.equals(value)) {
            return 0;
        }
        return -1;
    }

    public String set(final int index, final String element) {
        if (element == null) throw new IllegalArgumentException();

        final byte size = this.size;
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        final Object value = this.value;
        if (size == 1 && value instanceof String) try {
            return (String) value;
        } finally {
            this.value = element;
        } else {
            final String[] list = (String[]) value;
            final int i = index(index);
            try {
                return list[i];
            } finally {
                list[i] = element;
            }
        }
    }

    public boolean addAll(int index, final Collection<? extends String> c) {
        final int size = this.size;
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();
        final Iterator<? extends String> iterator = c.iterator();
        boolean result = false;
        while (iterator.hasNext()) { result |= offer(index, iterator.next()); }
        return result;
    }

    public List<String> subList(final int fromIndex, final int toIndex) {
        // todo - this is about 75% correct, by spec...
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) throw new IndexOutOfBoundsException();
        final int len = toIndex - fromIndex;
        final String[] strings = new String[len];
        for (int i = 0; i < len; i ++) {
            strings[i] = get(i + fromIndex);
        }
        return Arrays.asList(strings);
    }

    public String[] toArray() {
        int size = this.size;
        if (size == 0) { return NO_STRINGS; }
        final Object v = this.value;
        if (v instanceof String) return new String[] { (String) v };
        final String[] list = (String[]) v;
        final int head = this.head;
        final int len = list.length;
        final int copyEnd = head + size;
        if (copyEnd < len) {
            return Arrays.copyOfRange(list, head, copyEnd);
        } else {
            String[] ret = Arrays.copyOfRange(list, head, copyEnd);
            System.arraycopy(list, 0, ret, len - head, copyEnd - len);
            return ret;
        }
    }

    public <T> T[] toArray(final T[] a) {
        int size = this.size;
        if (size == 0) return a;
        final int inLen = a.length;
        final Object[] target = inLen < size ? Arrays.copyOfRange(a, inLen, inLen + size) : a;
        final Object v = this.value;
        if (v instanceof String) {
            target[0] = (T)v;
        } else {
            final String[] list = (String[]) v;
            final int head = this.head;
            final int len = list.length;
            final int copyEnd = head + size;
            if (copyEnd < len) {
                System.arraycopy(list, head, target, 0, size);
            } else {
                final int wrapEnd = len - head;
                System.arraycopy(list, head, target, 0, wrapEnd);
                System.arraycopy(list, 0, target, wrapEnd, copyEnd - len);
            }
        }
        return (T[]) target;
    }

    //======================================
    //
    // Derived methods
    //
    //======================================

    public void addFirst(final String s) {
        if (s == null) return;
        if (! offerFirst(s)) throw new IllegalStateException();
    }

    public void addLast(final String s) {
        if (s == null) return;
        if (! offerLast(s)) throw new IllegalStateException();
    }

    public void add(final int index, final String s) {
        if (s == null) return;
        if (! offer(index, s)) throw new IllegalStateException();
    }

    public boolean contains(final Object o) {
        return indexOf(o) != -1;
    }

    public String peekFirst() {
        return size == 0 ? null : get(0);
    }

    public String peekLast() {
        return size == 0 ? null : get(size - 1);
    }

    public boolean removeFirstOccurrence(final Object o) {
        int i = indexOf(o);
        return i != -1 && remove(i) != null;
    }

    public boolean removeLastOccurrence(final Object o) {
        int i = lastIndexOf(o);
        return i != -1 && remove(i) != null;
    }

    public boolean add(final String s) {
        addLast(s);
        return true;
    }

    public void push(final String s) {
        addFirst(s);
    }

    public String pop() {
        return removeFirst();
    }

    public boolean offer(final String s) {
        return offerLast(s);
    }

    public String poll() {
        return pollFirst();
    }

    public String peek() {
        return peekFirst();
    }

    public String remove() {
        return removeFirst();
    }

    public String removeFirst() {
        final String s = pollFirst();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public String removeLast() {
        final String s = pollLast();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public String getFirst() {
        final String s = peekFirst();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public String getLast() {
        final String s = peekLast();
        if (s == null) {
            throw new NoSuchElementException();
        }
        return s;
    }

    public String element() {
        return getFirst();
    }

    public boolean remove(Object obj) {
        return removeFirstOccurrence(obj);
    }

    public boolean addAll(final Collection<? extends String> c) {
        return addAll(0, c);
    }
}
