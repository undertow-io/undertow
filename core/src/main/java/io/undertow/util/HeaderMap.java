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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * An optimized array-backed header map.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public final class HeaderMap implements Iterable<HeaderValues> {

    private final HttpHeaders headers;

    public HeaderMap(HttpHeaders headers) {
        this.headers = headers;
    }

    private HeaderValues getEntry(final HttpString headerName) {
        List<String> res = headers.getAll(headerName.toString());
        if (res.isEmpty()) {
            return null;
        }
        return new HeaderValues(headers, headerName.toString(), res);
    }


    private HeaderValues getEntry(final String headerName) {
        List<String> res = headers.getAll(headerName);
        if (res.isEmpty()) {
            return null;
        }
        return new HeaderValues(headers, headerName, res);
    }

    private HeaderValues removeEntry(final HttpString headerName) {
        List<String> res = headers.getAll(headerName.toString());
        headers.remove(headerName.toString());
        if (res.isEmpty()) {
            return null;
        }
        return new HeaderValues(headers, headerName.toString(), res);
    }

    // get

    public HeaderValues get(final HttpString headerName) {
        return getEntry(headerName);
    }

    public HeaderValues get(final String headerName) {
        return getEntry(headerName);
    }

    public String getFirst(HttpString headerName) {
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getFirst();
    }

    public String getFirst(String headerName) {
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getFirst();
    }

    public String get(HttpString headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return null;
        }
        return headerValues.get(index);
    }

    public String get(String headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return null;
        }
        return headerValues.get(index);
    }

    public String getLast(HttpString headerName) {
        if (headerName == null) {
            return null;
        }
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getLast();
    }

    public String getLast(String headerName) {
        if (headerName == null) {
            return null;
        }
        HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) return null;
        return headerValues.getLast();
    }

    // count

    public int count(HttpString headerName) {
        if (headerName == null) {
            return 0;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return 0;
        }
        return headerValues.size();
    }

    public int count(String headerName) {
        if (headerName == null) {
            return 0;
        }
        final HeaderValues headerValues = getEntry(headerName);
        if (headerValues == null) {
            return 0;
        }
        return headerValues.size();
    }

    public int size() {
        return headers.size();
    }

    public Iterable<String> eachValue(final HttpString headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final HeaderValues entry = getEntry(headerName);
        if (entry == null) {
            return Collections.emptyList();
        }
        return entry;
    }

    public Iterator<HeaderValues> iterator() {
        return headers.names().stream().map((s) -> new HeaderValues(headers, s, headers.getAll(s))).collect(Collectors.toCollection(HashSet::new)).iterator();
    }

    public Collection<HttpString> getHeaderNames() {
        return headers.names().stream().map(HttpString::new).collect(Collectors.toCollection(HashSet::new));
    }

    // add

    public HeaderMap add(HttpString headerName, String headerValue) {
        addLast(headerName, headerValue);
        return this;
    }

    public HeaderMap addFirst(final HttpString headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return this;
        }
        //todo: order does not matter for headers
        headers.add(headerName.toString(), headerValue);
        return this;
    }

    public HeaderMap addLast(final HttpString headerName, final String headerValue) {
        headers.add(headerName.toString(), headerValue);
        return this;
    }

    public HeaderMap add(HttpString headerName, long headerValue) {
        add(headerName, Long.toString(headerValue));
        return this;
    }


    public HeaderMap addAll(HttpString headerName, Collection<String> headerValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValues == null || headerValues.isEmpty()) {
            return this;
        }
        headers.add(headerName.toString(), headerValues);
        return this;
    }

    // put

    public HeaderMap put(HttpString headerName, String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            remove(headerName);
            return this;
        }
        headers.set(headerName.toString(), headerValue);
        return this;
    }

    public HeaderMap put(HttpString headerName, long headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        headers.set(headerName.toString(), Long.toString(headerValue));
        return this;
    }

    public HeaderMap putAll(HttpString headerName, Collection<String> headerValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValues == null || headerValues.isEmpty()) {
            remove(headerName);
            return this;
        }
        headers.set(headerName.toString(), headerValues);
        return this;
    }

    // clear

    public HeaderMap clear() {
        headers.clear();
        return this;
    }

    // remove

    public Collection<String> remove(HttpString headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final Collection<String> values = removeEntry(headerName);
        return values != null ? values : Collections.<String>emptyList();
    }

    public Collection<String> remove(String headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        List<String> res = headers.getAll(headerName);
        headers.remove(headerName);
        return res;
    }

    // contains

    public boolean contains(HttpString headerName) {
        return headers.contains(headerName.toString());
    }

    public boolean contains(String headerName) {
        return headers.contains(headerName);
    }

    // compare

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
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (HttpString name : getHeaderNames()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
            sb.append("=[");
            boolean f = true;
            for (String val : get(name)) {
                if (f) {
                    f = false;
                } else {
                    sb.append(", ");
                }
                sb.append(val);
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }
}
