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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * An array-backed list/deque for header string values.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public final class HeaderValues extends AbstractCollection<String> implements Deque<String>, List<String>, RandomAccess {

    final HttpHeaders headers;
    final String headerName;
    final List<String> currentValues;

    public HeaderValues(HttpHeaders headers, String headerName, List<String> currentValues) {
        this.headers = headers;
        this.headerName = headerName;
        this.currentValues = new ArrayList<>(currentValues);
    }

    public HttpString getHeaderName() {
        return new HttpString(headerName);
    }

    public int size() {
        return currentValues.size();
    }

    public boolean isEmpty() {
        return currentValues.size() == 0;
    }

    public void clear() {
        currentValues.clear();
        headers.remove(headerName);
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
                return idx < size();
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
        currentValues.add(0, headerValue);
        update();
        return true;
    }

    public boolean offerLast(final String headerValue) {
        currentValues.add(headerValue);
        update();
        return true;
    }

    public String pollFirst() {
        if(currentValues.isEmpty()) {
            return null;
        }
        return currentValues.remove(0);
    }

    public String pollLast() {
        if(currentValues.isEmpty()) {
            return null;
        }
        return currentValues.remove(currentValues.size() - 1);
    }

    public String remove(int idx) {
        String res = currentValues.remove(idx);
        update();
        return res;
    }

    public String get(int idx) {
        return currentValues.get(idx);
    }

    public int indexOf(final Object o) {
        return currentValues.indexOf(o);
    }

    public int lastIndexOf(final Object o) {
        return currentValues.lastIndexOf(o);
    }

    public String set(final int index, final String element) {
        String ret = currentValues.set(index, element);
        update();
        return ret;
    }

    private void update() {
        headers.set(headerName, currentValues);
    }

    public boolean addAll(int index, final Collection<? extends String> c) {
        boolean result = currentValues.addAll(index, c);
        update();
        return result;
    }

    public List<String> subList(final int fromIndex, final int toIndex) {
        List<String> result = currentValues.subList(fromIndex, toIndex);
        update();
        return result;
    }

    public String[] toArray() {
        return (String[]) currentValues.toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return currentValues.toArray(a);
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
        currentValues.add(index, s);
        update();
    }

    public boolean contains(final Object o) {
        return indexOf(o) != -1;
    }

    public String peekFirst() {
        return currentValues.size() == 0 ? null : get(0);
    }

    public String peekLast() {
        return size() == 0 ? null : get(size() - 1);
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
        for (String s : c) {
            add(s);
        }
        return !c.isEmpty();
    }
}
