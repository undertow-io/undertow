/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

import java.util.Objects;

/**
 * An object adapter that wraps {@link PathTemplatePattern} and that uses its patternHashCode and patternEquals methods as the
 * standard hashCode and equals methods. The objective is to enable the use of "patterns" as keys for maps / sets etc.
 *
 * @author Dirk Roets
 *
 * @param <T> Type of path template patterns.
 */
public class PathTemplatePatternEqualsAdapter<T extends PathTemplatePattern> {

    private final T pattern;

    /**
     * @param pattern The pattern.
     */
    public PathTemplatePatternEqualsAdapter(final T pattern) {
        this.pattern = Objects.requireNonNull(pattern);
    }

    @Override
    public String toString() {
        return "PatternEqualsAdapter{" + "element=" + pattern + '}';
    }

    @Override
    public int hashCode() {
        return pattern.patternHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PathTemplatePatternEqualsAdapter<?> other = (PathTemplatePatternEqualsAdapter<?>) obj;
        return this.pattern.patternEquals(other.pattern);
    }

    /**
     * @return The underlying pattern.
     */
    public T getPattern() {
        return pattern;
    }
}
