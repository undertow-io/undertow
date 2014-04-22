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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AttachmentList<T> implements List<T>, RandomAccess {

    private final Class<T> valueClass;
    private final List<T> delegate;

    public AttachmentList(final int initialCapacity, final Class<T> valueClass) {
        delegate = Collections.checkedList(new ArrayList<T>(initialCapacity), valueClass);
        this.valueClass = valueClass;
    }

    public AttachmentList(final Class<T> valueClass) {
        delegate = Collections.checkedList(new ArrayList<T>(), valueClass);
        this.valueClass = valueClass;
    }

    public AttachmentList(final Collection<? extends T> c, final Class<T> valueClass) {
        delegate = Collections.checkedList(new ArrayList<T>(c.size()), valueClass);
        delegate.addAll(c);
        this.valueClass = valueClass;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean contains(final Object o) {
        return delegate.contains(o);
    }

    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    public Object[] toArray() {
        return delegate.toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return delegate.toArray(a);
    }

    public boolean add(final T t) {
        return delegate.add(t);
    }

    public boolean remove(final Object o) {
        return delegate.remove(o);
    }

    public boolean containsAll(final Collection<?> c) {
        return delegate.containsAll(c);
    }

    public boolean addAll(final Collection<? extends T> c) {
        return delegate.addAll(c);
    }

    public boolean addAll(final int index, final Collection<? extends T> c) {
        return delegate.addAll(index, c);
    }

    public boolean removeAll(final Collection<?> c) {
        return delegate.removeAll(c);
    }

    public boolean retainAll(final Collection<?> c) {
        return delegate.retainAll(c);
    }

    public void clear() {
        delegate.clear();
    }

    public boolean equals(final Object o) {
        return delegate.equals(o);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public T get(final int index) {
        return delegate.get(index);
    }

    public T set(final int index, final T element) {
        return delegate.set(index, element);
    }

    public void add(final int index, final T element) {
        delegate.add(index, element);
    }

    public T remove(final int index) {
        return delegate.remove(index);
    }

    public int indexOf(final Object o) {
        return delegate.indexOf(o);
    }

    public int lastIndexOf(final Object o) {
        return delegate.lastIndexOf(o);
    }

    public ListIterator<T> listIterator() {
        return delegate.listIterator();
    }

    public ListIterator<T> listIterator(final int index) {
        return delegate.listIterator(index);
    }

    public List<T> subList(final int fromIndex, final int toIndex) {
        return delegate.subList(fromIndex, toIndex);
    }
}
