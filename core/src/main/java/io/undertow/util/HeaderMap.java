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

package io.undertow.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * This implementation sucks and is incomplete.  It's just here to illustrate.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HeaderMap implements Iterable<HttpString> {

    private final Map<HttpString, Object> values = new TreeMap<>();

    public Iterator<HttpString> iterator() {
        return values.keySet().iterator();
    }

    public String getFirst(HttpString headerName) {
        Object value = values.get(headerName);
        if(value instanceof List) {
            return ((List<String>) value).get(0);
        } else {
            return (String)value;
        }
    }

    public String getLast(HttpString headerName) {
        Object value = values.get(headerName);
        if(value instanceof List) {
            List<String> list = (List<String>) value;
            return list.get(list.size()-1);
        } else {
            return (String)value;
        }
    }

    public List<String> get(HttpString headerName) {
        Object value = values.get(headerName);
        if(value == null) {
            return null;
        } else if(value instanceof List) {
            return (List<String>)value;
        } else {
            return Collections.singletonList((String) value);
        }
    }

    public void add(HttpString headerName, String headerValue) {
        final Object value = values.get(headerName);
        if (value == null) {
            values.put(headerName, headerValue);
        } else {
            if(value instanceof List) {
                ((List) value).add(headerValue);
            } else {
                final ArrayList<String> list = new ArrayList<String>(1);
                list.add((String) value);
                list.add(headerValue);
                values.put(headerName, list);
            }
        }
    }

    public void add(HttpString headerName, long headerValue) {
        add(headerName, Long.toString(headerValue));
    }


    public void addAll(HttpString headerName, Collection<String> headerValues) {
        final Object value = values.get(headerName);
        if (value == null) {
            values.put(headerName, new ArrayList<>(headerValues));
        } else {
            if(value instanceof List) {
                ((List) value).addAll(headerValues);
            } else {
                final ArrayList<String> list = new ArrayList<String>(1);
                list.add((String) value);
                list.addAll(headerValues);
                values.put(headerName, list);
            }
        }
    }

    public void clear() {
        values.clear();
    }

    public Collection<HttpString> getHeaderNames() {
        return new HashSet<HttpString>(values.keySet());
    }

    public void put(HttpString headerName, String headerValue) {
        values.put(headerName, headerValue);
    }

    public void put(HttpString headerName, long headerValue) {
        values.put(headerName, Long.toString(headerValue));
    }

    public void putAll(HttpString headerName, Collection<String> headerValues) {
        final ArrayList<String> list = new ArrayList<>(headerValues);
        values.put(headerName, list);
    }

    public Collection<String> remove(HttpString headerName) {
        Object value = values.remove(headerName);
        if(value instanceof List) {
            return (Collection<String>) value;
        } else {
            return Collections.singletonList((String)value);
        }
    }

    /**
     * Lock this header map to make it immutable.  This method is idempotent.
     */
    public void lock() {

    }

    public boolean contains(HttpString headerName) {
        final Object value = values.get(headerName);
        return value != null;
    }

    @Override
    public boolean equals(final Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "HeaderMap{" +
                "values=" + values +
                '}';
    }
}
