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

package io.undertow.server.handlers.form;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import io.undertow.util.SecureHashMap;

/**
 * Representation of form data.
 *
 * TODO: add representation of multipart data
 *
 */
public final class FormData implements Iterable<String> {


    static class FormValue extends ArrayDeque<String> {
        private final String name;

        FormValue(final String name) {
            super(1);
            this.name = name;
        }

        FormValue(final String name, final String singleValue) {
            this(name);
            add(singleValue);
        }

        FormValue(final String name, final Collection<String> c) {
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

            final FormValue strings = (FormValue) o;

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

    private final Map<String, FormValue> values = new SecureHashMap<String, FormValue>();

    public Iterator<String> iterator() {
        final Iterator<FormValue> iterator = values.values().iterator();
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

    public String getFirst(String name) {
        final Deque<String> deque = values.get(name);
        return deque == null ? null : deque.peekFirst();
    }

    public String getLast(String name) {
        final Deque<String> deque = values.get(name);
        return deque == null ? null : deque.peekLast();
    }

    public Deque<String> get(String name) {
        return values.get(name);
    }

    public void add(String name, String value) {
        final FormValue values = this.values.get(name);
        if (values == null) {
            this.values.put(name, new FormValue(name, value));
        } else {
            values.add(value);
        }
    }

    public void addAll(String name, Collection<String> formValues) {
        final FormValue value = values.get(name);
        if (value == null) {
            values.put(name, new FormValue(name, formValues));
        } else {
            value.addAll(formValues);
        }
    }

    public void addAll(FormData other) {
        for (Map.Entry<String, FormValue> entry : other.values.entrySet()) {
            final String key = entry.getKey();
            final FormValue value = entry.getValue();
            final FormValue target = values.get(key);
            if (target == null) {
                values.put(key, new FormValue(value.getName(), value));
            } else {
                target.addAll(value);
            }
        }
    }

    public void put(String name, String formValue) {
        final FormValue value = new FormValue(name, formValue);
        values.put(name, value);
    }

    public void putAll(String name, Collection<String> formValue) {
        final FormValue deque = new FormValue(name, formValue);
        values.put(name, deque);
    }

    public Collection<String> remove(String name) {
        return values.remove(name);
    }

    public boolean contains(String name) {
        final FormValue value = values.get(name);
        return value != null && ! value.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final FormData strings = (FormData) o;

        if (values != null ? !values.equals(strings.values) : strings.values != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return values != null ? values.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "FormData{" +
                "values=" + values +
                '}';
    }
}
