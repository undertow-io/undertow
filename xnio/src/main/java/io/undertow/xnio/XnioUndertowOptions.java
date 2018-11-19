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

package io.undertow.xnio;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

import io.undertow.UndertowOptions;
import io.undertow.connector.UndertowOption;
import io.undertow.connector.UndertowOptionMap;

public class XnioUndertowOptions {

    private static final IdentityHashMap<UndertowOption<?>, Option<?>> optionMap = new IdentityHashMap<>();
    private static final IdentityHashMap<Option<?>, UndertowOption<?>> reverseOptionMap = new IdentityHashMap<>();

    static {

        Map<String, Option<?>> xnio = new HashMap<>();
        Map<String, UndertowOption<?>> undertow = new HashMap<>();

        for (Field f : UndertowOptions.class.getFields()) {
            if(f.getType() != UndertowOption.class) {
                continue;
            }
            try {
                UndertowOption<?> opt = ((UndertowOption) f.get(null));
                undertow.put(opt.getName(), opt);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        for (Field f : Options.class.getFields()) {
            if(f.getType() != Option.class) {
                continue;
            }
            try {
                Option<?> opt = ((Option) f.get(null));
                xnio.put(opt.getName(), opt);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        for (Map.Entry<String, Option<?>> i : xnio.entrySet()) {
            UndertowOption<?> ut = undertow.get(i.getKey());
            if (ut != null) {
                optionMap.put(ut, i.getValue());
                reverseOptionMap.put(i.getValue(), ut);
            }
        }
    }

    public static synchronized <T> Option<T> key(UndertowOption<T> key) {
        if (!optionMap.containsKey(key)) {
            Option<T> simple = Option.simple(XnioUndertowOptions.class, key.getName(), key.getType());
            optionMap.put(key, simple);
            reverseOptionMap.put(simple, key);
        }
        return (Option<T>) optionMap.get(key);
    }

    public static synchronized <T> UndertowOption<T> key(Option<T> key) {
        if (!reverseOptionMap.containsKey(key)) {
            throw new IllegalStateException();
        }
        return (UndertowOption<T>) reverseOptionMap.get(key);
    }

    public static OptionMap map(UndertowOptionMap map) {
        OptionMap.Builder b = OptionMap.builder();
        for (Map.Entry<UndertowOption<?>, Object> i : map) {
            b.set((Option) key(i.getKey()), i.getValue());
        }
        return b.getMap();
    }

    public static UndertowOptionMap map(OptionMap map) {
        UndertowOptionMap.Builder b = UndertowOptionMap.builder();
        for (Option<?> i : map) {
            b.set((UndertowOption) key(i), map.get(i));
        }
        return b.getMap();
    }
}
