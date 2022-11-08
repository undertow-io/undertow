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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class allows to store multiple values for single key value. Key and value order are honored.
 * Duplicate entries of key-value pair is not allowed, it is vetted via {@link java.lang.Object#equals()} method.
 * @author baranowb
 *
 * @param <K> - key type
 * @param <V> - value type
 */
public class MultiValueHashListStorage<K, V> {
    // NOTE: requests are processed by single thread so no need to sync?
    private LinkedHashMap<K, ArrayList<V>> storage = new LinkedHashMap<>();

    public void put(K key, V value) {
        ArrayList<V> lst = storage.get(key);
        if (lst == null) {
            lst = new ArrayList<>();
            lst.add(value);
            storage.put(key, lst);
        } else {
            if (lst.contains(value)) {
                lst.remove(value);
            }
            lst.add(value);
        }
    }

    public Iterator<V> valuesIterator() {

        return new DefaultIterator<V>(this.storage);
    }

    public int size() {
        int size = 0;
        for (Entry<K, ArrayList<V>> kvp : storage.entrySet()) {
            final List<V> lst = kvp.getValue();
            if (lst != null) {
                size += lst.size();
            }
        }
        return size;
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public boolean containsKey(Object key) {
        return storage.containsKey(key);
    }

    public boolean containsValue(Object value) {
        for (Entry<K, ArrayList<V>> kvp : storage.entrySet()) {
            final List<V> lst = kvp.getValue();
            if (lst != null && lst.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<V> get(K key) {
        final ArrayList<V> values = storage.get(key);
        return values != null ? values : new ArrayList<V>();
    }

    public Collection<V> removeAll(Object key) {
        final ArrayList<V> values = storage.remove(key);
        return values != null ? values : new ArrayList<V>();
    }

    @SuppressWarnings("unchecked")
    public V remove(Object key, Object value) {
        final ArrayList<V> values = storage.get(key);
        if (values.contains(value)) {
            values.remove(value);
            if (values.size() == 0) {
                storage.remove(key);
            }
            return (V) value;
        } else {
            return null;
        }
    }

    public Set<K> keySet() {
        return storage.keySet();
    }

    public void clear() {
        storage.clear();
    }

    private class DefaultIterator<V> implements Iterator<V> {

        private final Iterator<Entry<K, ArrayList<V>>> storageSource;
        private Iterator<V> perKeySource;

        DefaultIterator(LinkedHashMap<K, ArrayList<V>> storage) {
            storageSource = storage.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            if (storageSource.hasNext()) {
                checkForValidValuesSource();
                if (perKeySource != null && perKeySource.hasNext()) {
                    return true;
                }
            } else if (perKeySource != null && perKeySource.hasNext()) {
                return true;
            }
            return false;
        }

        private void checkForValidValuesSource() {
            while (storageSource.hasNext()) {
                if (perKeySource != null && perKeySource.hasNext()) {
                    return;
                } else {
                    ArrayList<V> list = storageSource.next().getValue();
                    if (list != null) {
                        Iterator<V> possibleMatch = list.iterator();
                        if (possibleMatch.hasNext()) {
                            this.perKeySource = possibleMatch;
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public V next() {
            if (hasNext()) {
                return perKeySource.next();
            }
            return null;

        }

    }

}
