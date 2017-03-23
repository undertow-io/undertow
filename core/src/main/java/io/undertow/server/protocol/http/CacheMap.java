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

package io.undertow.server.protocol.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class CacheMap<K, V> extends LinkedHashMap<K, V> {
    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final long serialVersionUID = 1L;
    private int capacity;

    public CacheMap(int capacity) {
        super(capacity, DEFAULT_LOAD_FACTOR, true);
        this.capacity = capacity;
    }

    /**
     * removeEldestEntry() should be overridden by the user, otherwise it will not
     * remove the oldest object from the Map.
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > this.capacity;
    }
}
