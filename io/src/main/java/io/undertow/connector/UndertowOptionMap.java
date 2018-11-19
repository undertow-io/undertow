/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.connector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UndertowOptionMap implements Iterable<Map.Entry<UndertowOption<?>, Object>> {

    public static final UndertowOptionMap EMPTY = new UndertowOptionMap(Collections.emptyMap());


    private final Map<UndertowOption<?>, Object> values;

    UndertowOptionMap(Map<UndertowOption<?>, Object> values) {
        this.values = values;
    }

    public <T> T get(UndertowOption<T> option, T defaultValue) {
        return (T) values.getOrDefault(option, defaultValue);
    }

    public boolean get(UndertowOption<Boolean> option, boolean defaultValue) {
        return (Boolean) values.getOrDefault(option, defaultValue);
    }

    public <T> T get(UndertowOption<T> option) {
        return (T) values.get(option);
    }

    public boolean contains(UndertowOption<?> option) {
        return values.containsKey(option);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Iterator<Map.Entry<UndertowOption<?>, Object>> iterator() {
        return values.entrySet().iterator();
    }

    public static <T> UndertowOptionMap create(UndertowOption<T> key, T val) {
        return builder().set(key, val).getMap();
    }

    public static <T1, T2> UndertowOptionMap create(UndertowOption<T1> key1, T1 val1, UndertowOption<T2> key2, T2 val2) {
        return builder().set(key1, val1).set(key2, val2)
                .getMap();
    }

    public static class Builder {

        private final Map<UndertowOption<?>, Object> values = new HashMap<>();

        public <T> Builder set(UndertowOption<T> option, T value) {
            values.put(option, value);
            return this;
        }

        public Builder addAll(UndertowOptionMap workerOptions) {
            values.putAll(workerOptions.values);
            return this;
        }

        public UndertowOptionMap getMap() {
            return new UndertowOptionMap(new HashMap<>(values));
        }
    }
}
