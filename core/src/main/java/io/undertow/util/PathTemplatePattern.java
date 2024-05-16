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

/**
 * Interface for components of path templates that represent patterns. This interface is implemented by all three types of path
 * template segments (one type of component) as well as by the path template container (the second type of component) itself.
 *
 * Two patterns are considered to be the same (equal) when ALL possible paths that match the one pattern also matches the second
 * pattern.
 *
 * For example, the following two path templates are not equal due to different variable names being used, but the patterns
 * represented by the two paths are equal as all paths that match the first template will also match the second template:
 * <ol>
 * <li>/some/{varNameA}/url</li>
 * <li>/some/{varNameB}/url</li>
 * </ol>
 *
 * In the above mentioned example the {@link #hashCode() } method may return different values for the two templates and
 * {@link #equals(java.lang.Object) } will return 'false'. The {@link #patternHashCode() } will return the same values for the
 * two templates and {@link #patternEquals(io.undertow.util.PathTemplatePattern) } will return 'true'.
 *
 * <b>Custom implementations of this interface are not supported by the {@link PathTemplateRouterFactory}.</b>
 *
 * @author Dirk Roets
 */
public interface PathTemplatePattern {

    /**
     * @return A hash code for the pattern represented by this component.
     */
    int patternHashCode();

    /**
     * True if the pattern represented by this component is equal to the pattern represented by the specified component.
     *
     * @param pattern The pattern.
     *
     * @return True if this pattern represents the same 'pattern' as the specified pattern.
     */
    boolean patternEquals(PathTemplatePattern pattern);
}
