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
package io.undertow.server;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;

import io.undertow.server.handlers.Cookie;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class MapDelegatingToSet extends HashMap<String, Cookie> {

    private final Set<Cookie> delegate;

    MapDelegatingToSet(final Set<Cookie> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Cookie get(final Object key) {
        if (key == null) return null;
        for (Cookie cookie : delegate) {
            if (key.equals(cookie.getName())) return cookie;
        }
        return null;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (key == null) return false;
        for (Cookie cookie : delegate) {
            if (key.equals(cookie.getName())) return true;
        }
        return false;
    }

    @Override
    public Cookie put(final String key, final Cookie value) {
        if (key == null) return null;
        final Cookie retVal = remove(key);
        if (value != null) {
            delegate.add(value);
        }
        return retVal;
    }

    @Override
    public void putAll(final Map<? extends String, ? extends Cookie> m) {
        if (m == null) return;
        for (Map.Entry<? extends String, ? extends Cookie> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Cookie remove(final Object key) {
        if (key == null) return null;
        Cookie removedValue = null;
        for (Cookie cookie : delegate) {
            if (key.equals(cookie.getName())) {
                removedValue = cookie;
                break;
            }
        }
        if (removedValue != null) delegate.remove(removedValue);
        return removedValue;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean containsValue(final Object value) {
        if (value == null) return false;
        return delegate.contains(value);
    }

    @Override
    public Set<String> keySet() {
        if (delegate.isEmpty()) return emptySet();
        final Set<String> retVal = new HashSet<>();
        for (Cookie cookie : delegate) {
            retVal.add(cookie.getName());
        }
        return unmodifiableSet(retVal);
    }

    @Override
    public Collection<Cookie> values() {
        return delegate.isEmpty() ? emptySet() : unmodifiableCollection(delegate);
    }

    @Override
    public Set<Entry<String, Cookie>> entrySet() {
        if (delegate.isEmpty()) return emptySet();
        final Set<Entry<String, Cookie>> retVal = new HashSet<>(delegate.size());
        for (Cookie cookie : delegate) {
            retVal.add(new ReadOnlyEntry(cookie.getName(), cookie));
        }
        return unmodifiableSet(retVal);
    }

    @Override
    public Cookie getOrDefault(final Object key, final Cookie defaultValue) {
        if (key == null) return null;
        final Cookie retVal = get(key);
        return retVal != null ? retVal : defaultValue;
    }

    @Override
    public Cookie putIfAbsent(final String key, final Cookie value) {
        if (key == null) return null;
        final Cookie oldVal = get(key);
        if (oldVal == null) delegate.add(value);
        return oldVal;
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        if (key == null || value == null) return false;
        Cookie removedValue = null;
        for (Cookie cookie : delegate) {
            if (cookie == value) {
                removedValue = cookie;
                break;
            }
        }
        if (removedValue != null) delegate.remove(removedValue);
        return removedValue != null;
    }

    @Override
    public boolean replace(final String key, final Cookie oldValue, final Cookie newValue) {
        if (key == null) return false;
        final Cookie previousValue = get(key);
        if (previousValue == oldValue) {
            delegate.remove(oldValue);
            if (newValue != null) {
                delegate.add(newValue);
            }
            return true;
        }
        return false;
    }

    @Override
    public Cookie replace(final String key, final Cookie value) {
        if (key == null) return null;
        final Cookie oldValue = get(key);
        if (oldValue != null) {
            delegate.remove(oldValue);
            if (value != null) {
                delegate.add(value);
            }
        }
        return oldValue;
    }

    @Override
    public Cookie computeIfAbsent(final String key, final Function<? super String, ? extends Cookie> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cookie computeIfPresent(String key, BiFunction<? super String, ? super Cookie, ? extends Cookie> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cookie compute(String key, BiFunction<? super String, ? super Cookie, ? extends Cookie> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cookie merge(String key, Cookie value, BiFunction<? super Cookie, ? super Cookie, ? extends Cookie> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Cookie> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super Cookie, ? extends Cookie> function) {
        throw new UnsupportedOperationException();
    }

    private static final class ReadOnlyEntry implements Entry<String, Cookie> {
        private final String key;
        private final Cookie value;

        private ReadOnlyEntry(final String key, final Cookie value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Cookie getValue() {
            return value;
        }

        @Override
        public Cookie setValue(final Cookie cookie) {
            throw new UnsupportedOperationException();
        }
    }

}
