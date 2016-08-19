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

package io.undertow.server.handlers.encoding;

import io.undertow.predicate.Predicate;

/**
* @author Stuart Douglas
*/
final class EncodingMapping implements Comparable<EncodingMapping> {

    private final String name;
    private final ContentEncodingProvider encoding;
    private final int priority;
    private final Predicate allowed;

    EncodingMapping(final String name, final ContentEncodingProvider encoding, final int priority, final Predicate allowed) {
        this.name = name;
        this.encoding = encoding;
        this.priority = priority;
        this.allowed = allowed;
    }

    public String getName() {
        return name;
    }

    public ContentEncodingProvider getEncoding() {
        return encoding;
    }

    public int getPriority() {
        return priority;
    }

    public Predicate getAllowed() {
        return allowed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncodingMapping)) return false;

        EncodingMapping that = (EncodingMapping) o;
        return this.compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return getPriority();
    }

    @Override
    public int compareTo(final EncodingMapping o) {
        return priority - o.priority;
    }
}
