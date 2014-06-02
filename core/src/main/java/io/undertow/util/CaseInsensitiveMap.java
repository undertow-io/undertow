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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A case insensitive map
 *
 * @author Stuart Douglas
 */
public class CaseInsensitiveMap<V> implements Map<String, V> {

    private final HashMap<String, V> delegate = new HashMap<String, V>();

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if(key instanceof String) {
            return delegate.containsKey(((String) key).toLowerCase());
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        if(key instanceof String) {
            return delegate.get(((String) key).toLowerCase());
        }
        return null;
    }

    @Override
    public V put(String key, V value) {
        return delegate.put(key.toLowerCase(), value);
    }

    @Override
    public V remove(Object key) {
        if(key instanceof String) {
            return delegate.remove(((String) key).toLowerCase());
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> m) {
        for(Entry<? extends String, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<String> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public Set<Entry<String, V>> entrySet() {
        return delegate.entrySet();
    }
}
