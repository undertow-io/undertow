/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Temporary data structure to ensure compatibility with previous data retention strategies( cookies )
 * @author baranowb
 *
 * @param <K>
 * @param <V>
 * @see {@link #MultiValueHashListStorage}
 */
@Deprecated
class MapDelegatingToMultiValueStorage<K, V> implements Map<K, V> {

    private final MultiValueHashListStorage<K, V> target;

    MapDelegatingToMultiValueStorage(MultiValueHashListStorage<K, V> target) {
        super();
        this.target = target;
    }

    @Override
    public int size() {
        // this wont be accurate
        return target.size();
    }

    @Override
    public boolean isEmpty() {
        return target.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return target.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return target.containsValue(value);
    }

    @Override
    public V get(Object key) {
        // this will roughly mimic it
        Collection<V> lst = target.get((K) key);
        if (lst != null && lst.size() > 0) {
            return lst.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public V put(K key, V value) {
        Collection<V> rems = target.removeAll(key);
        target.put(key, value);
        if (rems != null && rems.size() > 0) {
            return rems.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public V remove(Object key) {
        Collection<V> rems = target.removeAll(key);
        if (rems != null && rems.size() > 0) {
            return rems.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for(Entry<? extends K, ? extends V> vk:m.entrySet()) {
            target.removeAll(vk.getKey());
        }
        for(Entry<? extends K, ? extends V> vk:m.entrySet()) {
            target.put(vk.getKey(),vk.getValue());
        }
    }

    @Override
    public void clear() {
        target.clear();
    }

    @Override
    public Set<K> keySet() {
        return target.keySet();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new RuntimeException();
    }

}
