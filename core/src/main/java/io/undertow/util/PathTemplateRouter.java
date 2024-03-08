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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Classes and methods for routing URL paths.
 *
 * The objective of this class is to provide a very fast router for URL paths with support for path parameters.
 *
 * Instances of this class are thread-safe.
 *
 * @author Dirk Roets dirkroets@gmail.com
 * @since 2023-03-07
 */
/*
 * Warning:
 * You have to be VERY familiar with the code encapsulated inside this class before you modify
 * anything. There are many inter dependencies amongst different inner classes, for example: The routers and matchers
 * do not have any unnecessary checks whilst routing requests - as an optimisation - based on guarantees made by the
 * router factory during instantiation of the routers and matchers. There is also a mutable object being passed around
 * to avoid certain overheads that would be introduced with using the String class or copying arrays etc. I am aware
 * that the code in this class could be made a lot more robust from a maintenance point of view, but the objective here
 * was specifically to provide something that is very fast even at the expense of maintainable code. The router
 * does a very simple thing and there should arguably be no need to constantly work on this code.
 */
public class PathTemplateRouter {

    //<editor-fold defaultstate="collapsed" desc="PatternElement inner class">
    /**
     * Interface for elements that represent a URL path pattern. The objective of this interface is to provide
     * a contract for comparing path patterns. For example:
     * <ol>
     * <li>/some/{varNameA}/url</li>
     * <li>/some/{varNameB}/url</li>
     * </ol>
     *
     * These templates have different path parameter names and are therefore not equal to each other, but the patterns
     * that they represent are considered to be equal.
     *
     * <b>Custom implementations of this interface are not supported by the {@link PathTemplateRouter}.</b>
     */
    public interface PatternElement {

        /**
         * @return A hash code for the pattern represented by this element.
         */
        int patternHashCode();

        /**
         * True if the pattern represented by this element is equal to the pattern represented by the specified element.
         *
         * @param obj The element.
         *
         * @return True if this element will pattern match the specified element.
         */
        boolean patternEquals(Object obj);
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="PatternEqualsAdapter inner class">
    /**
     * An object adapter that wraps a {@link PatternElement} and that uses its patternHashCode and patternEquals
     * methods as the standard hashCode and equals methods. The objective is to enable the use of "patterns" as
     * keys for maps / sets etc.
     *
     * @param <T> Type of pattern elements.
     */
    public static final class PatternEqualsAdapter<T extends PatternElement> implements
            Comparable<PatternEqualsAdapter<T>> {

        private final T element;

        /**
         * @param element The pattern element.
         */
        public PatternEqualsAdapter(final T element) {
            this.element = Objects.requireNonNull(element);
        }

        @Override
        public String toString() {
            return "PatternEqualsAdapter{" + "element=" + element + '}';
        }

        @Override
        public int hashCode() {
            return element.patternHashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final PatternEqualsAdapter<?> other = (PatternEqualsAdapter<?>) obj;
            return this.element.patternEquals(other.element);
        }

        /**
         * @return The underlying element.
         */
        public T getElement() {
            return element;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(final PatternEqualsAdapter<T> o) {
            if (element instanceof TemplateSegment) {
                if (o.element instanceof TemplateSegment)
                    return compareMostSpecificToLeastSpecific(
                            (PatternEqualsAdapter<TemplateSegment>) this,
                            (PatternEqualsAdapter<TemplateSegment>) o
                    );
                return 1;
            } else if (o.element instanceof TemplateSegment) {
                return -1;
            }
            return element.patternHashCode() - o.element.patternHashCode();
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Template segment inner classes">
    /**
     * Parent class for all segments inside a URL path. For the purposes of this class a segment is defined as the
     * parts of a path that are delimited by '/' characters.
     *
     * Extensions of this class must be immutable.
     */
    private abstract static class TemplateSegment implements PatternElement {

        /**
         * Index for the segment inside of the template.
         */
        protected final int segmentIdx;

        private TemplateSegment(final int segmentIdx) {
            this.segmentIdx = segmentIdx;

            if (segmentIdx < 0)
                throw new IllegalArgumentException();
        }
    }

    /**
     * A segment inside a pattern that is intended to match the corresponding segment of a requested URL exactly.
     *
     * Instances of this class are immutable.
     */
    private static class TemplateStaticSegment extends TemplateSegment {

        private final String value;

        private TemplateStaticSegment(
                final int segmentIdx,
                final String value
        ) {
            super(segmentIdx);
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public String toString() {
            return "TemplateStaticSegment{" + "segmentIdx=" + segmentIdx + ", value=" + value + '}';
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 67 * hash + this.segmentIdx;
            hash = 67 * hash + Objects.hashCode(this.value);
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final TemplateStaticSegment other = (TemplateStaticSegment) obj;
            if (this.segmentIdx != other.segmentIdx)
                return false;
            return Objects.equals(this.value, other.value);
        }

        @Override
        public int patternHashCode() {
            return hashCode();
        }

        @Override
        public boolean patternEquals(final Object obj) {
            return equals(obj);
        }
    }

    /**
     * A segment inside a pattern that is intended to match match any content from the corresponding segment of a
     * requested URL and that sets the value of the parameter with the associated name to the content of the
     * corresponding segment of the requested URL.
     *
     * Instances of this class are immutable.
     */
    private static class TemplateParamSegment extends TemplateSegment {

        /**
         * Name for the parameter, excluding the '{' and '}' braces.
         */
        private final String paramName;

        private TemplateParamSegment(
                final int segmentIdx,
                final String paramName
        ) {
            super(segmentIdx);
            this.paramName = Objects.requireNonNull(paramName);
        }

        @Override
        public String toString() {
            return "TemplateParamSegment{" + "segmentIdx=" + segmentIdx + ", paramName=" + paramName + '}';
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + this.segmentIdx;
            hash = 53 * hash + Objects.hashCode(this.paramName);
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final TemplateParamSegment other = (TemplateParamSegment) obj;
            if (this.segmentIdx != other.segmentIdx)
                return false;
            return Objects.equals(this.paramName, other.paramName);
        }

        @Override
        public int patternHashCode() {
            int hash = 5;
            hash = 53 * hash + this.segmentIdx;
            return hash;
        }

        @Override
        public boolean patternEquals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final TemplateParamSegment other = (TemplateParamSegment) obj;
            return this.segmentIdx == other.segmentIdx;
        }
    }

    /**
     * A segment inside a pattern that matches an optional prefix of the corresponding segment of a requested URL
     * and that matches anything after that in the requested URL.
     *
     * Instances of this class are immutable.
     */
    private static class TemplateWildCardSegment extends TemplateSegment {

        /**
         * Prefix for the wild card, excluding the '*' itself. May be an empty string.
         */
        private final String prefix;

        private TemplateWildCardSegment(
                final int segmentIdx,
                final String prefix
        ) {
            super(segmentIdx);
            this.prefix = Objects.requireNonNull(prefix);
        }

        @Override
        public String toString() {
            return "TemplateWildCardSegment{" + "segmentIdx=" + segmentIdx + ", prefix=" + prefix + '}';
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + this.segmentIdx;
            hash = 53 * hash + Objects.hashCode(this.prefix);
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final TemplateWildCardSegment other = (TemplateWildCardSegment) obj;
            if (this.segmentIdx != other.segmentIdx)
                return false;
            return Objects.equals(this.prefix, other.prefix);
        }

        @Override
        public int patternHashCode() {
            return hashCode();
        }

        @Override
        public boolean patternEquals(final Object obj) {
            return equals(obj);
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Template inner class">
    /**
     * Template - sequence of segments - for an entire URL path.
     *
     * @param <T> Target type.
     */
    public static class Template<T> implements PatternElement {

        private final String pathTemplate;
        private final List<TemplateSegment> segments;
        private final T target;
        private final boolean wildCard;

        private Template(
                final String pathTemplate,
                final List<TemplateSegment> segments,
                final boolean wildCard,
                final T target
        ) {
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
            this.segments = Objects.requireNonNull(segments);
            this.wildCard = wildCard;
            this.target = Objects.requireNonNull(target);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.segments);
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final Template<?> other = (Template<?>) obj;
            if (this.wildCard != other.wildCard)
                return false;
            if (!Objects.equals(this.segments, other.segments))
                return false;
            return Objects.equals(this.target, other.target);
        }

        @Override
        public int patternHashCode() {
            int hash = 7;
            for (final TemplateSegment segment : segments)
                hash = 97 * hash + segment.patternHashCode();
            return hash;
        }

        @Override
        public boolean patternEquals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            final Template<?> other = (Template<?>) obj;
            if (wildCard != other.wildCard)
                return false;
            if (segments.size() != other.segments.size())
                return false;

            final Iterator<TemplateSegment> it = segments.iterator();
            final Iterator<TemplateSegment> otherIt = other.segments.iterator();
            while (it.hasNext())
                if (!it.next().patternEquals(otherIt.next()))
                    return false;
            return true;
        }

        /**
         * @return The template string from which this template was created.
         */
        public String getPathTemplate() {
            return pathTemplate;
        }

        /**
         * @return The target for this template.
         */
        public T getTarget() {
            return target;
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RouteRequest inner class">
    private static class RouteRequest {

        private final char[] path;
        private final int pathLen;
        private final int[] segmentStartIndexes;
        private final int segmentCount;

        private RouteRequest(
                final char[] path,
                final int pathLen,
                final int[] segmentStartIndexes,
                final int segmentCount
        ) {
            this.path = path;
            this.pathLen = pathLen;
            this.segmentStartIndexes = segmentStartIndexes;
            this.segmentCount = segmentCount;
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RouteResult inner class">
    /**
     * Result from a routing request.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    public static class RouteResult<T> extends PathTemplateMatch {

        private final T target;
        private final Optional<String> pathTemplate;

        private RouteResult(
                final T target,
                final Optional<String> pathTemplate,
                final Map<String, String> parameters
        ) {
            super(
                    pathTemplate.orElse(""),
                    Objects.requireNonNull(parameters)
            );
            this.target = Objects.requireNonNull(target);
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
        }

        @Override
        public String toString() {
            return "RouteResult{" + "target=" + target + ", pathTemplate=" + pathTemplate + ", paramValues=" + getParameters() + '}';
        }

        /**
         * @return The target.
         */
        public T getTarget() {
            return target;
        }

        /**
         * @return The matched template, if any.
         */
        public Optional<String> getPathTemplate() {
            return pathTemplate;
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Matcher inner classes">
    /**
     * A strategy that accepts a route request, attempts to match it against the structure represented by the
     * strategy (matcher) and that returns an appropriate result for the request based on whether or not the request
     * 'matches' the structure.
     *
     * Implementations must be thread-safe.
     *
     * @param <T> Target type.
     */
    @FunctionalInterface
    private interface Matcher<T> extends Function<RouteRequest, RouteResult<T>> {
    }

    /**
     * Calls the underlying 'nextMatcher' if the associated segment is equal to the value contained by this matcher.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Type of target.
     */
    private static class SegmentEqualsMatcher<T> implements Matcher<T> {

        private final RouteResult<T> defaultResult;
        private final char[] value;
        private final int len;
        private final int segmentIdx;
        private final Matcher<T> nextMatcher;

        private SegmentEqualsMatcher(
                final RouteResult<T> defaultResult,
                final char[] value,
                final int segmentIdx,
                final Matcher<T> nextMatcher
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.value = Objects.requireNonNull(value);
            this.len = value.length;
            this.segmentIdx = segmentIdx;
            this.nextMatcher = Objects.requireNonNull(nextMatcher);

            if (this.segmentIdx < 0)
                throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "SegmentEqualsMatcher{" + "value=" + String.copyValueOf(value) + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;
            if (segmentLength != len)
                return defaultResult;

            for (int i = 0; i < segmentLength; i++) {
                if (value[i] != request.path[segmentStartIdx + i])
                    return defaultResult;
            }

            return nextMatcher.apply(request);
        }
    }

    /**
     * Calls the underlying 'nextMatcher' if the associated segment starts with the value contained by this matcher.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SegmentStartsWithMatcher<T> implements Matcher<T> {

        private final RouteResult<T> defaultResult;
        private final char[] value;
        private final int len;
        private final int segmentIdx;
        private final Matcher<T> nextMatcher;

        private SegmentStartsWithMatcher(
                final RouteResult<T> defaultResult,
                final char[] value,
                final int segmentIdx,
                final Matcher<T> nextMatcher
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.value = Objects.requireNonNull(value);
            this.len = value.length;
            this.segmentIdx = segmentIdx;
            this.nextMatcher = Objects.requireNonNull(nextMatcher);

            if (this.len < 1)
                throw new IllegalArgumentException();
            if (this.segmentIdx < 0)
                throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "SegmentStartsWithMatcher{" + "value=" + String.copyValueOf(value) + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;
            if (segmentLength < len)
                return defaultResult;

            for (int i = 0; i < len; i++) {
                if (value[i] != request.path[segmentStartIdx + i])
                    return defaultResult;
            }
            return nextMatcher.apply(request);
        }
    }

    private static class BinarySearchRouterMatcher<T> implements Matcher<T> {

        private final RouteResult<T> defaultResult;
        private final SegmentEqualsMatcher<T>[] matchers;
        private final int matchersLen;
        private final int segmentIdx;
        private final int minSegmentLength;
        private final int maxSegmentLength;

        private BinarySearchRouterMatcher(
                final RouteResult<T> defaultResult,
                final List<? extends SegmentEqualsMatcher<? extends T>> matchers
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.matchers = sortedSegmentEqualsMatchers(matchers);
            this.matchersLen = this.matchers.length;

            if (this.matchersLen == 0)
                throw new IllegalArgumentException();

            this.segmentIdx = this.matchers[0].segmentIdx;

            int i = 0;
            for (final SegmentEqualsMatcher<?> m : this.matchers)
                if (m.len > i)
                    i = m.len;
            this.maxSegmentLength = i;
            for (final SegmentEqualsMatcher<?> m : this.matchers)
                if (m.len < i)
                    i = m.len;
            this.minSegmentLength = i;
        }

        @Override
        public String toString() {
            return "BinarySearchRouterMatcher{" + "matchersLen=" + matchersLen + '}';
        }

        private static int compare(final char[] c1, final char[] c2, final int len) {
            int result;
            for (int i = 0; i < len; i++) {
                result = c1[i] - c2[i];
                if (result != 0)
                    return result;
            }
            return 0;
        }

        private static int compare(final SegmentEqualsMatcher<?> s1, final SegmentEqualsMatcher<?> s2) {
            int result = Integer.compare(s1.len, s2.len);
            if (result != 0)
                return result;

            return compare(s1.value, s2.value, s1.len);
        }

        @SuppressWarnings("unchecked")
        private static <T> SegmentEqualsMatcher<T>[] sortedSegmentEqualsMatchers(
                final List<? extends SegmentEqualsMatcher<? extends T>> matchers
        ) {
            Objects.requireNonNull(matchers);

            final int len = matchers.size();
            final SegmentEqualsMatcher[] result = new SegmentEqualsMatcher[len];
            int idx = 0;
            for (final SegmentEqualsMatcher<? extends T> matcher : matchers)
                result[idx++] = matcher;

            Arrays.sort(result, BinarySearchRouterMatcher::compare);
            return result;
        }

        private static int compare(
                //First object - the request.
                final int requestSegmentStartIdx,
                final int requestSegmentLength,
                final char[] requestPath,
                //Second object - the matcher
                final SegmentEqualsMatcher<?> matcher
        ) {
            if (requestSegmentLength < matcher.len)
                return -1;
            if (requestSegmentLength > matcher.len)
                return 1;

            int result;
            for (int i = 0; i < requestSegmentLength; i++) {
                result = requestPath[requestSegmentStartIdx + i] - matcher.value[i];
                if (result != 0)
                    return result;
            }
            return 0;
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;

            //We don't evaluate the static matchers if none of them has the correct length to match this segment anyway
            if (segmentLength < this.minSegmentLength || segmentLength > this.maxSegmentLength)
                return defaultResult;

            //Binary search
            int low = 0, high = this.matchersLen - 1, mid, cmp;
            SegmentEqualsMatcher<T> midVal;
            while (low <= high) {
                mid = (low + high) >>> 1;
                midVal = this.matchers[mid];
                cmp = compare(segmentStartIdx, segmentLength, request.path, midVal);
                if (cmp < 0) {
                    high = mid - 1;
                } else if (cmp > 0) {
                    low = mid + 1;
                } else {
                    //Match found
                    final RouteResult<T> result = midVal.nextMatcher.apply(request);
                    return result;
                }
            }
            return defaultResult;
        }
    }

    /**
     * The terminal stage for a path that doesn't contain parameters or wildcards. It will be called once all
     * preceding checks have passed and only returns the target result.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CreateSimpleResultMatcher<T> implements Matcher<T> {

        private final RouteResult<T> result;

        private CreateSimpleResultMatcher(
                final RouteResult<T> result
        ) {
            this.result = Objects.requireNonNull(result);
        }

        @Override
        public String toString() {
            return "CreateSimpleResultMatcher{" + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            if (result.pathTemplate.isPresent()) {
                /*  This will be set in the exchange, meaning some developers may be tempted to mutate it. For
                    backwards compatibility sake, a new mutable map is created. */
                final Map<String, String> paramValues = new HashMap<>(result.getParameters());
                return new RouteResult<>(result.target, result.pathTemplate, paramValues);
            }
            return result;
        }
    }

    /**
     * The terminal stage for a path that contains parameters, but no wildcards. It will be called once all
     * preceding checks have passed, it populates the parameters and returns the target result along with the
     * parameters.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CreateResultWithParamsMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int[] paramSegmentIndexes;
        private final String[] paramNames;
        private final int paramCount;
        private final int paramMapSize;

        private CreateResultWithParamsMatcher(
                final T target,
                final Optional<String> pathTemplate,
                final int[] paramSegmentIndexes,
                final String[] paramNames
        ) {
            this.target = Objects.requireNonNull(target);
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
            this.paramSegmentIndexes = Objects.requireNonNull(paramSegmentIndexes);
            this.paramNames = Objects.requireNonNull(paramNames);
            this.paramCount = this.paramSegmentIndexes.length;
            this.paramMapSize = (int) (this.paramCount / 0.75d) + 1;

            if (this.paramCount != this.paramNames.length)
                throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "CreateResultWithParamsMatcher{" + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            return new RouteResult<>(
                    target,
                    pathTemplate,
                    createParams(paramSegmentIndexes, paramNames, paramCount, paramMapSize, request)
            );
        }
    }

    /**
     * The terminal stage for a path that doesn't contain parameters, but that does contain a wild card. It will be
     * called once all preceding checks have passed and only returns the target result along with a single parameter,
     * being that part of the path that was matched by the wild card.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CreateSimpleWildCardResultMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int segmentIdx;
        private final int prefixLength;

        private CreateSimpleWildCardResultMatcher(
                final T target,
                final Optional<String> pathTemplate,
                final int segmentIdx,
                final int prefixLength
        ) {
            this.target = Objects.requireNonNull(target);
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
            this.segmentIdx = segmentIdx;
            this.prefixLength = prefixLength;

            if (segmentIdx < 0)
                throw new IllegalArgumentException();
            if (prefixLength < 0)
                throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "CreateSimpleWildCardResultMatcher{" + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            final int startIdx = request.segmentStartIndexes[segmentIdx] + prefixLength;
            if (startIdx == (request.pathLen - 1)) {
                return new RouteResult<>(
                        target,
                        pathTemplate,
                        createParams("*", "")
                );
            }

            final String wildCardValue = new String(request.path, startIdx, request.pathLen - startIdx);

            return new RouteResult<>(
                    target,
                    pathTemplate,
                    createParams("*", wildCardValue)
            );
        }
    }

    /**
     * The terminal stage for a path that contains parameters and that contains a wildcard. It will be called once all
     * preceding checks have passed, it populates the parameters and returns the target result along with the
     * parameters.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CreateWildCardWithParamsResultMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int[] paramSegmentIndexes;
        private final String[] paramNames;
        private final int paramCount;
        private final int paramMapSize;
        private final int segmentIdx;
        private final int prefixLength;

        private CreateWildCardWithParamsResultMatcher(
                final T target,
                final Optional<String> pathTemplate,
                final int[] paramSegmentIndexes,
                final String[] paramNames,
                final int segmentIdx,
                final int prefixLength
        ) {
            this.target = Objects.requireNonNull(target);
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
            this.paramSegmentIndexes = Objects.requireNonNull(paramSegmentIndexes);
            this.paramNames = Objects.requireNonNull(paramNames);
            this.paramCount = this.paramSegmentIndexes.length;
            this.paramMapSize = (int) ((this.paramCount + 1) / 0.75d) + 1;
            this.segmentIdx = segmentIdx;
            this.prefixLength = prefixLength;

            if (segmentIdx < 0)
                throw new IllegalArgumentException();
            if (prefixLength < 0)
                throw new IllegalArgumentException();
            if (paramCount != this.paramNames.length)
                throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "CreateWildCardWithParamsResultMatcher{" + '}';
        }

        private Map<String, String> createParams(
                final RouteRequest request,
                final String wildCardValue
        ) {
            final Map<String, String> result = PathTemplateRouter.createParams(
                    paramSegmentIndexes, paramNames, paramCount, paramMapSize, request
            );
            result.put("*", wildCardValue);
            return result;
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            final int startIdx = request.segmentStartIndexes[segmentIdx] + prefixLength;
            if (startIdx == (request.pathLen - 1)) {
                return new RouteResult<>(
                        target,
                        pathTemplate,
                        createParams(request, "")
                );
            }

            final String wildCardValue = new String(request.path, startIdx, request.pathLen - startIdx);

            return new RouteResult<>(
                    target,
                    pathTemplate,
                    createParams(request, wildCardValue)
            );
        }
    }

    /**
     * A composite matcher that tries all of the underlying matchers until a matcher returns a result other
     * than the default result, then it returns that result. Otherwise the default result is returned.
     *
     * @param <T> Type of target.
     */
    private static class CompositeMatcher<T> implements Matcher<T> {

        private final RouteResult<T> defaultResult;
        private final Matcher<T>[] matchers;
        private final int len;

        private CompositeMatcher(
                final RouteResult<T> defaultResult,
                final List<? extends Matcher<? extends T>> matchers
        ) {
            this.matchers = arrayOfMatchers(matchers);
            this.len = this.matchers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);

            if (this.len < 2)
                throw new IllegalArgumentException("Should not use a composite");
        }

        @Override
        public String toString() {
            return "CompositeMatcher{" + "len=" + len + '}';
        }

        @Override
        public RouteResult<T> apply(final RouteRequest request) {
            RouteResult<T> result;
            for (int i = 0; i < len; i++) {
                result = matchers[i].apply(request);
                if (result != defaultResult)
                    return result;
            }
            return defaultResult;
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Router inner classes">
    /**
     * A URL path router.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    public interface Router<T> extends Function<String, RouteResult<T>> {

        /**
         * @return The default target for requests that do no match any specific routes.
         */
        T getDefaultTarget();

        /**
         * Routes the path.
         *
         * @param path The path.
         *
         * @return The routing result.
         */
        @Override
        RouteResult<T> apply(String path);
    }

    /**
     * A simple router that routes paths containing static segments or parameters. Specifically this router has
     * an optimisation - based on the segment counts of requests - that do not support wild cards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SimpleRouter<T> implements Router<T> {

        private final int minSegmentCount;
        private final int maxSegmentCount;
        /*  This is a - potentially - sparse array.  Each element represents the matcher for a path with the same
            number of segments as the index of the array.  So the matcher in position 1 in the array is only applicable
            to paths with one segment and so on.  This makes it easy to quickly eliminate a large number of matchers
            as those will never be able to match the path anyway. */
        private final Matcher<T>[] matchers;
        private final RouteResult<T> defaultResult;

        private SimpleRouter(
                final Matcher<T>[] matchers,
                final RouteResult<T> defaultResult
        ) {
            this.matchers = Objects.requireNonNull(matchers);
            this.defaultResult = Objects.requireNonNull(defaultResult);

            this.minSegmentCount = getMinSegmentCount(matchers);
            this.maxSegmentCount = this.matchers.length - 1;
        }

        private static int getMinSegmentCount(final Matcher<?>[] matchers) {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i] != null)
                    return i;
            }
            throw new IllegalArgumentException("At least one segment matcher must be supplied");
        }

        @Override
        public String toString() {
            return "SimpleRouter{" + '}';
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.target;
        }

        @Override
        public RouteResult<T> apply(final String path) {
            //This router is empty.
            if (maxSegmentCount == 0)
                return defaultResult;

            final RouteRequest request = createRouteRequest(path, minSegmentCount, maxSegmentCount);

            /* Early exit when the request factory determined that we don't have a matcher with the resulting number
        of segments. */
            if (request == CANT_MATCH_REQUEST)
                return defaultResult;

            final Matcher<T> matcher = matchers[request.segmentCount];
            return matcher != null ? matcher.apply(request) : defaultResult;
        }
    }

    /**
     * A router that supports wild card matchers.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class WildCardRouter<T> implements Router<T> {

        private final int minSegmentCount;
        /* This is a - potentially - sparse array.  Each element represents the matcher for a path with the same
    number of segments as the index of the array.  So the matcher in position 1 in the array is only applicable
    to paths with one segment and so on.  This makes it easy to quickly eliminate a large number of matchers
    as those will never be able to match the path anyway. */
        private final Matcher<T>[] matchers;
        private final int len;
        private final RouteResult<T> defaultResult;

        private WildCardRouter(
                final Matcher<T>[] matchers,
                final RouteResult<T> defaultResult
        ) {
            this.matchers = Objects.requireNonNull(matchers);
            this.len = this.matchers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);

            this.minSegmentCount = getMinSegmentCount(matchers);
        }

        private static int getMinSegmentCount(final Matcher<?>[] matchers) {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i] != null)
                    return i;
            }
            throw new IllegalArgumentException("At least one segment matcher must be supplied");
        }

        @Override
        public String toString() {
            return "WildCardRouter{" + '}';
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.target;
        }

        @Override
        public RouteResult<T> apply(final String path) {
            final RouteRequest request = createRouteRequest(path, minSegmentCount, Integer.MAX_VALUE);

            /* Early exit when the request factory determined that we don't have a matcher with the resulting number
        of segments. */
            if (request == CANT_MATCH_REQUEST)
                return defaultResult;

            Matcher<T> matcher;
            RouteResult<T> result;
            for (int i = Math.min(len - 1, request.segmentCount); i > 0; i--) {
                matcher = matchers[i];
                if (matcher != null) {
                    result = matcher.apply(request);
                    if (result != defaultResult)
                        return result;
                }
            }
            return defaultResult;
        }
    }

    /**
     * A composite router of routers.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CompositeRouter<T> implements Router<T> {

        private final Router<T>[] routers;
        private final int len;
        private final RouteResult<T> defaultResult;

        private CompositeRouter(
                final Router<T>[] routers,
                final RouteResult<T> defaultResult
        ) {
            this.routers = Objects.requireNonNull(routers);
            this.len = this.routers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.target;
        }

        @Override
        public RouteResult<T> apply(final String path) {
            RouteResult<T> result;
            for (int i = 0; i < len; i++) {
                result = routers[i].apply(path);
                if (result != defaultResult)
                    return result;
            }
            return defaultResult;
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="MatcherLeaveArterfacts inner class">
    /**
     * Intermediate objects used to build the tree structure during creation of a router. Represents leaves of the tree
     * which are later turned into a tree of matchers by the router factory.
     *
     * Instances of this class ARE NOT thread-safe. These are only used by the factory and should therefore never
     * be used from multiple threads.
     *
     * @param <T> Target type.
     */
    private static class MatcherLeaveArterfacts<T> {

        private final Map<PatternEqualsAdapter<TemplateSegment>, MatcherLeaveArterfacts<T>> children
                = new HashMap<>();
        /**
         * Index of the segment represented by these artefacts.
         */
        private int segmentIdx;
        /**
         * The template with wich this leave is associated.
         */
        private Template<? extends T> template;
        /**
         * True if this leave is the root of a template.
         */
        private boolean rootLeave;
        /**
         * True if this leave is the last / deepest leave in a template.
         */
        private boolean finalLeave;
        /**
         * IF this leave is a static segment leave: The static segment for this leave.
         */
        private TemplateStaticSegment staticSegment;
        /**
         * IF this leave is a parameter segment leave: The index of the parameter in the array of parameters for the
         * template.
         */
        private int paramIdx = -1;
        /**
         * IF this leave is a parameter segment leave AND it is the final leave: Reference to the array of segment
         * indexes that are parameter segments in the current template.
         */
        private List<Integer> paramIndexes;
        /**
         * IF this leave is a parameter segment leave AND it is the final leave: Reference to the array of names for
         * parameters in the template.
         */
        private List<String> paramNames;
        /**
         * If this leave is a wild card segment: The wild card segment for this leave.
         */
        private TemplateWildCardSegment wildCardSegment;
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RouterFactory inner class">
    private static class RouterFactory<T> {

        // Input.
        private final Set<? extends Template<? extends Supplier<? extends T>>> templates;
        private final T defaultTarget;
        // Results and master temporary structures..
        private RouteResult<T> defaultResult;
        private int maxSegmentCount;
        private int maxWildCardSegmentCount;
        private MatcherLeaveArterfacts[] rootLeaves;
        private MatcherLeaveArterfacts[] wildCardRootLeaves;
        // Temporary structures used per call to processTemplate(Template).
        private Template<? extends Supplier<? extends T>> currentTemplate;
        private int currentSegmentCount;
        // Temporary structures used per call to processSegment(TemplateSegment).
        private List<TemplateSegment> segmentStack;
        private List<Integer> currentParamIndexes;
        private List<String> currentParamNames;
        private MatcherLeaveArterfacts<Supplier<? extends T>> currentLeave;
        private TemplateSegment currentSegment;
        // Resulting matchers.
        private Matcher[] matchers;
        private Matcher[] wildCardMatchers;

        private RouterFactory(
                final Set<? extends Template<? extends Supplier<? extends T>>> templates,
                final T defaultTarget
        ) {
            this.templates = Objects.requireNonNull(templates);
            this.defaultTarget = Objects.requireNonNull(defaultTarget);
        }

        private static <T> RouteResult<T> createDefaultResult(final T defaultTarget) {
            return new RouteResult<>(
                    defaultTarget,
                    Optional.empty(),
                    Collections.emptyMap()
            );
        }

        @SuppressWarnings("unchecked")
        private static <A> Matcher<A>[] toMatcherArray(final Collection<? extends Matcher<A>> matchers) {
            final Matcher[] result = new Matcher[matchers.size()];
            int idx = 0;
            for (final Matcher<?> m : matchers)
                result[idx++] = m;

            return result;
        }

        private SimpleRouter<T> createEmptyRouter() {
            /*  An immutable map here is okay, seeing that the PathTemplateMatch won't be set in the
                exchange and therefore not attempts can be made to mutate the map outside of the internal
                workings of the path template router and handlers. */
            final RouteResult<T> routeResult = new RouteResult<>(
                    defaultTarget, Optional.empty(), Map.of()
            );
            final Matcher<T> innerMatchers = new CreateSimpleResultMatcher<>(
                    routeResult
            );
            final Matcher<T>[] innerMatchersArray = toMatcherArray(List.of(innerMatchers));
            return new SimpleRouter<>(
                    innerMatchersArray,
                    routeResult
            );
        }

        private void initDefaultResults() {
            this.defaultResult = createDefaultResult(defaultTarget);
        }

        private void initMaxSegmentCounts() {
            int cnt;
            for (final Template<?> template : templates) {
                cnt = template.segments.size();
                if (template.wildCard) {
                    if (cnt > maxWildCardSegmentCount)
                        maxWildCardSegmentCount = cnt;
                } else {
                    if (cnt > maxSegmentCount)
                        maxSegmentCount = cnt;
                }
            }
        }

        private void initRootLeaves() {
            this.rootLeaves = new MatcherLeaveArterfacts[maxSegmentCount + 1];
            this.wildCardRootLeaves = new MatcherLeaveArterfacts[maxWildCardSegmentCount + 1];
        }

        private void initSegmentStack() {
            this.segmentStack = new ArrayList<>(maxSegmentCount);
        }

        private void initCurrentParamIndexes() {
            this.currentParamIndexes = new ArrayList<>(maxSegmentCount);
        }

        private void initCurrentParamNames() {
            this.currentParamNames = new ArrayList<>(maxSegmentCount);
        }

        @SuppressWarnings("unchecked")//Always safe based on the internal handling of this array.
        private MatcherLeaveArterfacts<Supplier<? extends T>> getCurrentRootLeave() {
            final MatcherLeaveArterfacts[] artefacts = (currentTemplate.wildCard)
                    ? wildCardRootLeaves : rootLeaves;

            MatcherLeaveArterfacts result = artefacts[currentSegmentCount];
            if (result != null)
                return result;

            result = new MatcherLeaveArterfacts();
            result.segmentIdx = -1;
            result.rootLeave = true;
            artefacts[currentSegmentCount] = result;
            return result;
        }

        private void processSegment(final TemplateSegment segment) {
            segmentStack.add(segment);
            currentSegment = segment;

            final PatternEqualsAdapter<TemplateSegment> adapter = new PatternEqualsAdapter<>(segment);
            MatcherLeaveArterfacts<Supplier<? extends T>> nextLeave = currentLeave.children.get(adapter);
            if (nextLeave == null) {
                nextLeave = new MatcherLeaveArterfacts<>();
                nextLeave.segmentIdx = segment.segmentIdx;
                currentLeave.children.put(adapter, nextLeave);
            }

            currentLeave = nextLeave;
            if (segment instanceof TemplateStaticSegment) {
                final TemplateStaticSegment staticSegment = (TemplateStaticSegment) segment;
                currentLeave.staticSegment = staticSegment;
            } else if (segment instanceof TemplateParamSegment) {
                final TemplateParamSegment paramSegment = (TemplateParamSegment) segment;
                currentLeave.paramIdx = currentParamNames.size();
                currentParamIndexes.add(paramSegment.segmentIdx);
                currentParamNames.add(paramSegment.paramName);
            } else if (segment instanceof TemplateWildCardSegment) {
                final TemplateWildCardSegment wildCardSegment = (TemplateWildCardSegment) segment;
                if (segmentStack.size() != currentSegmentCount)
                    throw new IllegalArgumentException("Wild cards are only supported at the end of a template path");
                currentLeave.paramIdx = currentParamNames.size();
                currentParamIndexes.add(wildCardSegment.segmentIdx);
                currentParamNames.add("*");
                currentLeave.wildCardSegment = wildCardSegment;
            } else {
                throw new IllegalArgumentException("Unsupported segment type");
            }
        }

        private void finalizeTemplate() {
            currentLeave.finalLeave = true;
            currentLeave.template = currentTemplate;

            if (!currentParamIndexes.isEmpty()) {
                currentLeave.paramIndexes = new ArrayList<>(currentParamIndexes);
                currentLeave.paramNames = new ArrayList<>(currentParamNames);
            }
        }

        private void processTemplate(final Template<? extends Supplier<? extends T>> template) {
            currentTemplate = template;
            currentSegmentCount = template.segments.size();
            currentLeave = getCurrentRootLeave();
            segmentStack.clear();
            currentParamIndexes.clear();
            currentParamNames.clear();
            template.segments.forEach(this::processSegment);
            finalizeTemplate();
        }

        private static String ident(final int indent) {
            final char[] result = new char[indent * 4];
            Arrays.fill(result, ' ');
            return new String(result);
        }

        private static void printNode(
                final int indent,
                final MatcherLeaveArterfacts<?> artefacts
        ) {
            System.out.println(
                    ident(indent) + "Node (" + (artefacts.staticSegment != null ? artefacts.staticSegment.value : artefacts.paramIdx) + ")");
            for (final MatcherLeaveArterfacts<?> child : artefacts.children.values()) {
                printNode(indent + 1, child);
            }
        }

        private void printNodes() {
            System.out.println("Nodes:");
            for (int i = 0; i < maxSegmentCount + 1; i++) {
                if (rootLeaves[i] != null)
                    printNode(0, rootLeaves[i]);
            }
            System.out.println();
            System.out.println("Wild Card Nodes:");
            for (int i = 0; i < maxWildCardSegmentCount + 1; i++) {
                if (rootLeaves[i] != null)
                    printNode(0, wildCardRootLeaves[i]);
            }
        }

        private void initMatchers() {
            this.matchers = new Matcher[maxSegmentCount + 1];
            this.wildCardMatchers = new Matcher[maxWildCardSegmentCount + 1];
        }

        private MatcherLeaveArterfacts<Supplier<? extends T>> getLeave(
                final MatcherLeaveArterfacts[] leaves,
                final int segmentCount
        ) {
            @SuppressWarnings("unchecked")
            final MatcherLeaveArterfacts<Supplier<? extends T>> result = leaves[segmentCount];
            return result;
        }

        private static <T> Matcher<T> createSimpleResultMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts) {
            final RouteResult<T> result = new RouteResult<>(
                    artefacts.template.target.get(),
                    Optional.of(artefacts.template.pathTemplate),
                    /*  This result will be copied with a mutable map by the CreateSimpleResultMatcher, therefore we
                        can start with an immutable map here. */
                    Map.of()
            );
            return new CreateSimpleResultMatcher<>(result);
        }

        private static <T> Matcher<T> createResultWithParamsMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts) {
            final int[] paramSegmentIndexes = new int[artefacts.paramIndexes.size()];
            final int len = paramSegmentIndexes.length;
            final String[] paramNames = new String[len];
            for (int i = 0; i < len; i++) {
                paramSegmentIndexes[i] = artefacts.paramIndexes.get(i);
                paramNames[i] = artefacts.paramNames.get(i);
            }

            return new CreateResultWithParamsMatcher<>(
                    artefacts.template.target.get(),
                    Optional.of(artefacts.template.pathTemplate),
                    paramSegmentIndexes,
                    paramNames
            );
        }

        private static <T> Matcher<T> createWildCardResultMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts
        ) {
            if (artefacts.paramIndexes.size() == 1) {
                return new CreateSimpleWildCardResultMatcher<>(
                        artefacts.template.target.get(),
                        Optional.of(artefacts.template.pathTemplate),
                        artefacts.wildCardSegment.segmentIdx,
                        artefacts.wildCardSegment.prefix.length()
                );
            } else {
                final int[] paramSegmentIndexes = new int[artefacts.paramIndexes.size() - 1];
                final int len = paramSegmentIndexes.length;
                final String[] paramNames = new String[len];
                for (int i = 0; i < len; i++) {
                    paramSegmentIndexes[i] = artefacts.paramIndexes.get(i);
                    paramNames[i] = artefacts.paramNames.get(i);
                }
                return new CreateWildCardWithParamsResultMatcher<>(
                        artefacts.template.target.get(),
                        Optional.of(artefacts.template.pathTemplate),
                        paramSegmentIndexes,
                        paramNames,
                        artefacts.wildCardSegment.segmentIdx,
                        artefacts.wildCardSegment.prefix.length()
                );
            }
        }

        private Matcher<T> createFinalMatcher(final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts) {
            if (!artefacts.children.isEmpty())
                throw new IllegalStateException();

            if (artefacts.paramNames == null)
                return createSimpleResultMatcher(artefacts);

            if (artefacts.wildCardSegment != null)
                return createWildCardResultMatcher(artefacts);

            return createResultWithParamsMatcher(artefacts);
        }

        private Matcher<T> createStaticSegmentMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts,
                final Matcher<T> nextMatcher
        ) {
            return new SegmentEqualsMatcher<>(
                    defaultResult,
                    artefacts.staticSegment.value.toCharArray(),
                    artefacts.staticSegment.segmentIdx,
                    nextMatcher
            );
        }

        private Matcher<T> createWildCardSegmentMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts,
                final Matcher<T> nextMatcher
        ) {
            if (artefacts.wildCardSegment.prefix.isEmpty())
                return nextMatcher;

            return new SegmentStartsWithMatcher<>(
                    defaultResult,
                    artefacts.wildCardSegment.prefix.toCharArray(),
                    artefacts.wildCardSegment.segmentIdx,
                    nextMatcher
            );
        }

        private Matcher<T> createSimpleMatcher(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts,
                final Matcher<T> nextMatcher
        ) {
            if (artefacts.staticSegment != null)
                return createStaticSegmentMatcher(artefacts, nextMatcher);
            else if (artefacts.wildCardSegment != null)
                return createWildCardSegmentMatcher(artefacts, nextMatcher);
            else if (artefacts.paramIdx != -1)
                return nextMatcher;
            else if (artefacts.rootLeave)
                return nextMatcher;
            throw new IllegalStateException("Should never happen");
        }

        private List<Matcher<T>> createSortedInnerMatchers(
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts
        ) {
            final List<PatternEqualsAdapter<TemplateSegment>> keys = new ArrayList<>(artefacts.children.keySet());
            Collections.sort(keys, PathTemplateRouter::compareMostSpecificToLeastSpecific);

            final List<Matcher<T>> result = new ArrayList<>(keys.size());
            for (final PatternEqualsAdapter<TemplateSegment> key : keys)
                result.add(createMatcher(artefacts.children.get(key)));
            return result;
        }

        private static <T> void addSegmentEqualsRouting(
                final RouteResult<T> defaultResult,
                final int innerSegmentIdx,
                final List<Matcher<T>> sortedInnerMatchers,
                final List<Matcher<T>> target
        ) {
            if (sortedInnerMatchers.isEmpty())
                return;

            final List<SegmentEqualsMatcher<T>> segmentEqualsMatchers = new LinkedList<>();
            final Iterator<Matcher<T>> it = sortedInnerMatchers.iterator();
            Matcher<T> matcher;
            while (it.hasNext()) {
                matcher = it.next();
                if (matcher instanceof SegmentEqualsMatcher<?>) {
                    final SegmentEqualsMatcher<T> segmentEqualsMatcher = (SegmentEqualsMatcher<T>) matcher;
                    if (segmentEqualsMatcher.segmentIdx == innerSegmentIdx) {
                        segmentEqualsMatchers.add(segmentEqualsMatcher);
                        it.remove();
                        continue;
                    }
                }
                break;
            }

            switch (segmentEqualsMatchers.size()) {
                case 0:
                    break;
                case 1:
                    target.add(segmentEqualsMatchers.get(0));
                    break;
                default:
                    target.add(new BinarySearchRouterMatcher<>(
                            defaultResult,
                            segmentEqualsMatchers
                    ));
                    break;
            }
        }

        private static <T> void addWildCardStartsWithRouting(
                final int innerSegmentIdx,
                final List<Matcher<T>> sortedInnerMatchers,
                final List<Matcher<T>> target
        ) {
            if (sortedInnerMatchers.isEmpty())
                return;

            final List<SegmentStartsWithMatcher<T>> segmentStartsWithMatchers = new LinkedList<>();
            final Iterator<Matcher<T>> it = sortedInnerMatchers.iterator();
            Matcher<T> matcher;
            while (it.hasNext()) {
                matcher = it.next();
                if (matcher instanceof SegmentStartsWithMatcher<?>) {
                    final SegmentStartsWithMatcher<T> segmentEqualsMatcher = (SegmentStartsWithMatcher<T>) matcher;
                    if (segmentEqualsMatcher.segmentIdx == innerSegmentIdx) {
                        segmentStartsWithMatchers.add(segmentEqualsMatcher);
                        it.remove();
                        continue;
                    }
                }
                break;
            }

            /* TODO Perhaps we need an optimised router for this case. For now we just let if fall through to the
            default composite router.  */
            target.addAll(segmentStartsWithMatchers);
        }

        private static <T> void addRemainingRouting(
                final List<Matcher<T>> sortedInnerMatchers,
                final List<Matcher<T>> target
        ) {
            target.addAll(sortedInnerMatchers);
        }

        private Matcher<T> createRouterMatcher(final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts) {
            final List<Matcher<T>> innerMatchers = createSortedInnerMatchers(artefacts);
            final List<Matcher<T>> routerMatchers = new LinkedList<>();
            final int innerSegmentIdx = artefacts.segmentIdx + 1;
            addSegmentEqualsRouting(defaultResult, innerSegmentIdx, innerMatchers, routerMatchers);
            addWildCardStartsWithRouting(innerSegmentIdx, innerMatchers, routerMatchers);
            addRemainingRouting(innerMatchers, routerMatchers);

            switch (routerMatchers.size()) {
                case 0:
                    throw new IllegalStateException("Should never happen");
                case 1:
                    return routerMatchers.get(0);
                default:
                    return new CompositeMatcher<>(
                            defaultResult,
                            routerMatchers
                    );
            }
        }

        /*  TODO The recursion here can be removed, but it also never goes deeper than the number of segments per path,
            so it should not be completely unreasonable to use recursion. */
        private Matcher<T> createMatcher(final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts) {
            //Exit if this is the end of the branch.
            if (artefacts.finalLeave) {
                //This matcher is just the strategy to generate the result.
                final Matcher<T> nextMatcher = createFinalMatcher(artefacts);
                return createSimpleMatcher(artefacts, nextMatcher);
            }

            //See how many children we have.
            final int childrenSize = artefacts.children.size();
            if (childrenSize == 0)
                throw new IllegalStateException("Should never happen");

            //If we have just one child, then the result needs to be a simple matcher.
            if (childrenSize == 1) {
                final Matcher<T> nextMatcher = createMatcher(artefacts.children.values().iterator().next());
                return createSimpleMatcher(artefacts, nextMatcher);
            }

            //This needs to be a routing matcher.
            final Matcher<T> router = createRouterMatcher(artefacts);
            return createSimpleMatcher(artefacts, router);
        }

        private void createMatchers() {
            for (int i = 0; i <= maxSegmentCount; i++) {
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts = getLeave(rootLeaves, i);
                if (artefacts != null)
                    matchers[i] = createMatcher(artefacts);
            }
            for (int i = 0; i <= maxWildCardSegmentCount; i++) {
                final MatcherLeaveArterfacts<Supplier<? extends T>> artefacts = getLeave(wildCardRootLeaves, i);
                if (artefacts != null)
                    wildCardMatchers[i] = createMatcher(artefacts);
            }
        }

        @SuppressWarnings("unchecked")//Always safe based on the internal handling of this array.
        private Matcher<T>[] getMatchers(final Matcher[] matchers) {
            return matchers;
        }

        @SuppressWarnings("unchecked")
        private static <T> Router<T>[] toRouterArray(final List<? extends Router<? extends T>> routers) {
            final Router[] result = new Router[routers.size()];
            int idx = 0;
            for (final Router<? extends T> router : routers)
                result[idx++] = router;
            return result;
        }

        /**
         * @return A new router instance base on this factory.
         */
        public Router<T> create() {
            if (templates.isEmpty())
                return createEmptyRouter();

            initDefaultResults();
            initMaxSegmentCounts();
            initRootLeaves();
            initSegmentStack();
            initCurrentParamIndexes();
            initCurrentParamNames();
            templates.forEach(this::processTemplate);
            if (DEBUG)
                printNodes();

            initMatchers();
            createMatchers();

            final List<Router<T>> routers = new LinkedList<>();
            if (maxSegmentCount > 0)
                routers.add(new SimpleRouter<>(
                        getMatchers(matchers),
                        defaultResult
                ));

            if (maxWildCardSegmentCount > 0)
                routers.add(new WildCardRouter<>(
                        getMatchers(wildCardMatchers),
                        defaultResult
                ));

            if (routers.size() == 1)
                return routers.get(0);

            return new CompositeRouter<>(
                    toRouterArray(routers),
                    defaultResult
            );
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Builder inner class">
    /**
     * Builder for {@link Router} instances.
     *
     * Instances of this class ARE NOT thread-safe, but the resulting routers are.
     *
     * @param <S> Factory type for building routes.
     * @param <T> Target type for built routes.
     */
    public static class Builder<S extends Supplier<T>, T> {

        private S defaultTargetFactory;
        private final Map<PatternEqualsAdapter<Template<S>>, S> templates = new HashMap<>();

        private Builder() {
        }

        /**
         * @return New builder instance.
         */
        public static Builder<Supplier<Void>, Void> newBuilder() {
            return new Builder<>();
        }

        /**
         * @return The default target factory.
         */
        public S getDefaultTargetFactory() {
            return defaultTargetFactory;
        }

        /**
         * @param defaultTargetFactory The default target factory.
         */
        public void setDefaultTargetFactory(final S defaultTargetFactory) {
            this.defaultTargetFactory = defaultTargetFactory;
        }

        /**
         * @param defaultTargetFactory The default target factory.
         *
         * @return Reference to the builder.
         */
        public Builder<S, T> defaultTargetFactory(final S defaultTargetFactory) {
            setDefaultTargetFactory(defaultTargetFactory);
            return this;
        }

        /**
         * Sets the default target to any type - provided that no templates have been added.
         *
         * @param <A>                  New target factory type.
         * @param <B>                  New target type.
         * @param defaultTargetFactory The default target factory.
         *
         * @return Reference to the builder.
         */
        @SuppressWarnings("unchecked") //Always safe based on the encapsulated features of this builder.
        public <A extends Supplier<B>, B> Builder<A, B> updateDefaultTargetFactory(final A defaultTargetFactory) {
            if (!templates.isEmpty())
                throw new IllegalStateException("updateDefaultTargetFactory cannot be called after having added "
                        + "templates. Use defaultTarget instead");

            Builder b = this;
            b.defaultTargetFactory = defaultTargetFactory;
            return b;
        }

        /**
         * Sets the default target to any type - provided that no templates have been added.
         *
         * @param <B>           New target type.
         * @param defaultTarget The default target.
         *
         * @return Reference to the builder.
         */
        @SuppressWarnings("unchecked") //Always safe based on the encapsulated features of this builder.
        public <B> Builder<Supplier<B>, B> updateDefaultTarget(final B defaultTarget) {
            Objects.requireNonNull(defaultTarget);

            if (!templates.isEmpty())
                throw new IllegalStateException("updateDefaultTarget cannot be called after having added "
                        + "templates. Use defaultTarget instead");

            Builder b = this;
            b.defaultTargetFactory = () -> defaultTarget;
            return b;
        }

        /**
         * Gets the existing target factory for the specified template, provided that one has already been added.
         *
         * @param template The template.
         *
         * @return The target factory or {@code NULL}.
         */
        public S getTemplateTarget(final Template<S> template) {
            final PatternEqualsAdapter<Template<S>> key = new PatternEqualsAdapter<>(template);
            return templates.get(key);
        }

        /**
         * @return A mutable map of {@link PatternEqualsAdapter}s for all templates added to this builder.
         */
        public Map<PatternEqualsAdapter<Template<S>>, S> getTemplates() {
            return templates;
        }

        /**
         * Clears all existing templates from this builder.
         *
         * @return Reference to the builder.
         */
        public Builder<S, T> clearTemplates() {
            templates.clear();
            return this;
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param template The template.
         *
         * @return Reference to the builder.
         */
        public Builder<S, T> addTemplate(final Template<S> template) {
            final PatternEqualsAdapter<Template<S>> item = new PatternEqualsAdapter<>(template);
            if (templates.containsKey(item))
                throw new IllegalArgumentException("The builder already contains a template with the same "
                        + "pattern for '" + template.pathTemplate + "'");
            templates.put(item, template.target);
            return this;
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param pathTemplate  The path template.
         * @param targetFactory The target factory.
         *
         * @return Reference to the builder.
         */
        public Builder<S, T> addTemplate(final String pathTemplate, final S targetFactory) {
            final Template<S> template = parseTemplate(pathTemplate, targetFactory);
            return Builder.this.addTemplate(template);
        }

        /**
         * Removes the specified path template from this builder.
         *
         * @param pathTemplate The path template.
         *
         * @return Reference to the builder.
         */
        public Builder<S, T> removeTemplate(final String pathTemplate) {
            final Template template = parseTemplate(
                    pathTemplate,
                    this
            );

            //Always safe based on how PatternEqualsAdapter uses the template in equals/hashCode
            @SuppressWarnings("unchecked")
            final PatternEqualsAdapter<Template<S>> item = new PatternEqualsAdapter<>(
                    (Template<S>) template
            );
            templates.remove(item);
            return this;
        }

        /**
         * @return A new router based on this builder.
         */
        public Router<T> build() {
            if (defaultTargetFactory == null)
                throw new IllegalStateException("The 'defaultTargetFactory' must be set before calling 'build'");

            return new RouterFactory<>(
                    templates.keySet().stream().map(t -> t.element).collect(Collectors.toSet()),
                    defaultTargetFactory.get()
            ).create();
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="SimpleBuilder inner class">
    /**
     * A simplistic version of {@link Builder} with convenience methods for adding targets directly without
     * using factories.
     *
     * Instances of this class ARE NOT thread-safe, but the resulting routers are.
     *
     * @param <T> Target type for built routes.
     */
    public static class SimpleBuilder<T> {

        private final Builder<Supplier<T>, T> builder;

        private SimpleBuilder(
                final Builder<Supplier<T>, T> builder
        ) {
            this.builder = Objects.requireNonNull(builder);
        }

        /**
         * Creates a new instance of the simple builder.
         *
         * @param <T>           Target type for built routes.
         * @param defaultTarget The default target. May be {@code NULL} in which case is should be specified
         *                      with a separate call to {@link #defaultTarget(java.lang.Object) }.
         *
         * @return Reference to the builder.
         */
        public static <T> SimpleBuilder<T> newBuilder(final T defaultTarget) {
            final Builder<Supplier<T>, T> builder = Builder.newBuilder()
                    .updateDefaultTarget(defaultTarget);
            return new SimpleBuilder<>(builder);
        }

        /**
         * Creates a new instance of the simple builder.
         *
         * @param <T> Target type for built routes.
         *
         * @return Reference to the builder.
         */
        public static <T> SimpleBuilder<T> newBuilder() {
            return newBuilder(null);
        }

        /**
         * @return The underlying {@link Builder}.
         */
        public Builder<Supplier<T>, T> getBuilder() {
            return builder;
        }

        /**
         * @return The default target factory.
         */
        public T getDefaultTarget() {
            final Supplier<T> tf = builder.getDefaultTargetFactory();
            return tf != null ? tf.get() : null;
        }

        /**
         * @param defaultTarget The default target.
         */
        public void setDefaultTarget(final T defaultTarget) {
            Objects.requireNonNull(defaultTarget);
            builder.setDefaultTargetFactory(() -> defaultTarget);
        }

        /**
         * @param defaultTarget The default target factory.
         *
         * @return Reference to the builder.
         */
        public SimpleBuilder<T> defaultTarget(final T defaultTarget) {
            setDefaultTarget(defaultTarget);
            return this;
        }

        /**
         * @return A mutable map of {@link PatternEqualsAdapter}s for all templates added to this builder.
         */
        public Map<PatternEqualsAdapter<Template<Supplier<T>>>, Supplier<T>> getTemplates() {
            return builder.getTemplates();
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param template The template.
         *
         * @return Reference to the builder.
         */
        public SimpleBuilder<T> addTemplate(final Template<Supplier<T>> template) {
            builder.addTemplate(template);
            return this;
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param pathTemplate  The path template.
         * @param targetFactory The target factory.
         *
         * @return Reference to the builder.
         */
        public SimpleBuilder<T> addTemplate(final String pathTemplate, final Supplier<T> targetFactory) {
            return addTemplate(parseTemplate(pathTemplate, targetFactory));
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param pathTemplate The path template.
         * @param target       The target.
         *
         * @return Reference to the builder.
         */
        public SimpleBuilder<T> addTemplate(final String pathTemplate, final T target) {
            Objects.requireNonNull(target);
            return addTemplate(pathTemplate, () -> target);
        }

        /**
         * Removes the specified path template from this builder.
         *
         * @param pathTemplate The path template.
         *
         * @return Reference to the builder.
         */
        public SimpleBuilder<T> removeTemplate(final String pathTemplate) {
            builder.removeTemplate(pathTemplate);
            return this;
        }

        /**
         * @return A new router based on this builder.
         */
        public Router<T> build() {
            return builder.build();
        }
    }

    //</editor-fold>
    //
    private static final RouteRequest EMPTY_REQUEST = new RouteRequest(new char[0], 0, new int[0], 0);
    private static final RouteRequest CANT_MATCH_REQUEST = new RouteRequest(new char[0], 0, new int[0], 0);
    private static final boolean DEBUG = false;

    private PathTemplateRouter() {
    }

    private static int compareMostSpecificToLeastSpecific(
            final PatternEqualsAdapter<TemplateSegment> o1,
            final PatternEqualsAdapter<TemplateSegment> o2
    ) {
        if (o1.element.segmentIdx != o2.element.segmentIdx)
            return o2.element.segmentIdx - o1.element.segmentIdx;

        if (o1.element instanceof TemplateStaticSegment) {
            final TemplateStaticSegment o1Static = (TemplateStaticSegment) o1.element;
            if (o2.element instanceof TemplateStaticSegment) {
                final TemplateStaticSegment o2Static = (TemplateStaticSegment) o2.element;
                return o2Static.value.length() - o1Static.value.length();
            }
            return -1;
        }

        if (o1.element instanceof TemplateParamSegment) {
            if (o2.element instanceof TemplateStaticSegment)
                return 1;

            if (o2.element instanceof TemplateParamSegment)
                return 0;

            if (o2.element instanceof TemplateWildCardSegment)
                return -1;

            throw new IllegalArgumentException("Should never happend");
        }

        if (o1.element instanceof TemplateWildCardSegment) {
            final TemplateWildCardSegment o1WildCard = (TemplateWildCardSegment) o1.element;
            if (o2.element instanceof TemplateStaticSegment)
                return 1;

            if (o2.element instanceof TemplateParamSegment)
                return 1;

            if (o2.element instanceof TemplateWildCardSegment) {
                final TemplateWildCardSegment o2WildCard = (TemplateWildCardSegment) o2.element;
                return o2WildCard.prefix.length() - o1WildCard.prefix.length();
            }
            throw new IllegalArgumentException("Should never happend");
        }

        throw new IllegalArgumentException("Should never happend");
    }

    @SuppressWarnings("unchecked")
    private static <T> Matcher<T>[] arrayOfMatchers(
            final List<? extends Matcher<? extends T>> matchers
    ) {
        final int len = matchers.size();
        final Matcher[] result = new Matcher[len];
        int idx = 0;
        for (final Matcher<? extends T> matcher : matchers)
            result[idx++] = matcher;
        return result;
    }

    /**
     * Creates a map of parameters - that are extracted from the specified request. The resulting map is mutable.
     *
     * @param paramSegmentIndexes Indexes of the segments that are parameters.
     * @param paramNames          Names for the parameters.
     * @param paramCount          Total count of parameters. Must be equal to paramSegmentIndexes.length and
     *                            paramNames.length.
     *                            Receiving this - instead of calculating is an optimisation that flows from the
     *                            Matcher strategies that use this method. Care must be taken to ensure that it is
     *                            accurate as it isn't validated during routing.
     * @param paramMapSize        Initial size of the resulting map. Provided as an optimisation in case the map needs
     *                            to cater for further values being added without having to resize/rebalance.
     * @param request             The request.
     *
     * @return The mutable map of parameters.
     */
    private static Map<String, String> createParams(
            final int[] paramSegmentIndexes,
            final String[] paramNames,
            final int paramCount,
            final int paramMapSize,
            final RouteRequest request
    ) {
        final Map<String, String> result = new HashMap<>(paramMapSize);
        int paramSegmentIndex;
        String paramName, paramValue;
        for (int i = 0; i < paramCount; i++) {
            paramSegmentIndex = paramSegmentIndexes[i];
            paramName = paramNames[i];
            paramValue = new String(
                    request.path,
                    request.segmentStartIndexes[paramSegmentIndex],
                    request.segmentStartIndexes[paramSegmentIndex + 1] - 1 - request.segmentStartIndexes[paramSegmentIndex]
            );
            result.put(paramName, paramValue);
        }
        return result;
    }

    /**
     * Creates a map of parameters that contains exactly one entry with the specified name and value. This is
     * essentially an alternative to {@link Map#of(java.lang.Object, java.lang.Object) } that returns a mutable
     * map.
     *
     * @param name  Name for the parameter.
     * @param value Value for the parameter.
     *
     * @return The mutable map of parameters.
     */
    private static Map<String, String> createParams(final String name, final String value) {
        final Map<String, String> result = new HashMap<>(2);
        result.put(name, value);
        return result;
    }

    private static int countSegments(final char[] path) {
        int len = path.length;
        if (len == 0 || path[0] != '/')
            throw new IllegalArgumentException();
        if (len == 1)
            return 0;
        if (path[len - 1] == '/')
            --len;

        int result = 1;
        char lastChar = '/';
        for (int i = 1; i < len; i++) {
            if (path[i] == '/') {
                if (lastChar == '/')
                    throw new IllegalArgumentException();
                ++result;
            }
            lastChar = path[i];
        }

        return result;
    }

    /**
     * Finds the first index in the array that contains the specified key.
     *
     * @param a         The array to be searched.
     * @param fromIndex The index of the first element (inclusive) to be
     *                  searched
     * @param toIndex   The index of the last element (exclusive) to be searched.
     * @param key       The value to be searched for.
     *
     * @return Index of the first element in the array - between fromIndex and toIndex - that is equal to the specified
     *         keys. Returns -1 if the specified key is not found between fromIndex and toIndex.
     *
     * @throws IllegalArgumentException       If {@code fromIndex > toIndex}.
     * @throws ArrayIndexOutOfBoundsException If {@code fromIndex < 0 or toIndex > a.length}.
     */
    private static int firstIndexOf(
            final char[] a,
            final int fromIndex,
            final int toIndex,
            final char key
    ) {
        final int arrayLength = a.length;
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }

        for (int i = fromIndex; i < toIndex; i++) {
            if (a[i] == key)
                return i;
        }
        return -1;
    }

    private static String validSegment(
            final char[] template,
            final int startIdx,
            final int endIdx
    ) {
        final String value = new String(template, startIdx, endIdx - startIdx + 1);

        char c;
        boolean valid;
        for (int i = startIdx; i <= endIdx; i++) {
            c = template[i];
            valid = (c >= '0' && c <= '9');
            valid = valid || (c >= 'a' && c <= 'z');
            valid = valid || (c >= 'A' && c <= 'Z');
            valid = valid || c == '_' || c == '-';

            if (!valid)
                throw new IllegalArgumentException("Illegal character '"
                        + c + "' is contained in the segment '" + value + "' at position " + i);
        }

        return value;
    }

    private static TemplateSegment createSegment(
            final int segmentIdx,
            final char[] template,
            final int startIdx,
            final int endIdx
    ) {
        // This is an empty static segment.  Mostly used by templates ending with '/' as the final segment.
        if (startIdx == endIdx)
            return new TemplateStaticSegment(segmentIdx, "");

        // Starts and ends with '{' and '}', implies that it is a parameter segment.
        if (template[startIdx] == '{' && template[endIdx - 1] == '}')
            return new TemplateParamSegment(segmentIdx, validSegment(template, startIdx + 1, endIdx - 2));

        // Contains an '*', implies that it is a wildcard segment.
        int idx = firstIndexOf(template, startIdx, endIdx, '*');
        if (idx != -1) {
            final String prefix = validSegment(template, startIdx, idx - 1);
            return new TemplateWildCardSegment(segmentIdx, prefix);
        }

        // Defaults to a static segment.
        return new TemplateStaticSegment(segmentIdx, validSegment(template, startIdx, endIdx - 1));
    }

    private static char[] pathWithForwardSlash(
            final String pathString,
            final int pathLength
    ) {
        if (pathLength == 0)
            return new char[]{'/'};

        final boolean startsWithForwardSlash = pathString.charAt(0) == '/';
        final char[] path = new char[pathLength + (startsWithForwardSlash ? 0 : 1)];
        if (startsWithForwardSlash) {
            pathString.getChars(0, pathLength, path, 0);
        } else {
            path[0] = '/';
            pathString.getChars(0, pathLength, path, 1);
        }
        return path;
    }

    private static RouteRequest createRouteRequest(
            final String pathString,
            final int minSegmentCount,
            final int maxSegmentCount
    ) {
        /*  Work directly with char array for performance reasons.  We remove the query string and ensure that the path
            starts with a '/'. */
        int pathLength = pathString.indexOf('?');
        if (pathLength == -1)
            pathLength = pathString.length();
        final char[] path = pathWithForwardSlash(pathString, pathLength);

        /*  We know the path starts with '/', so if the length is 1 then it is a request for root. If that is a routable
            path - based on minSegmentCount - then we return an empty request, otherwise we know it is not routable. */
        int pathLen = path.length;
        if (pathLen == 1)
            return minSegmentCount > 0 ? CANT_MATCH_REQUEST : EMPTY_REQUEST;

        /*  Quick count of the segments to create an array of the correct size. We also exit immediately if it becomes
            clear that we have less or more segments than the router has templates for */
        int cnt = 1;
        char c = '/';
        for (int i = 1; i < pathLen; i++) {
            if (path[i] == '/') {
                if (c == '/')
                    return CANT_MATCH_REQUEST;
                if (++cnt > maxSegmentCount)
                    return CANT_MATCH_REQUEST;
            }
            c = path[i];
        }
        if (cnt < minSegmentCount)
            return CANT_MATCH_REQUEST;

        //Let's create the segments.
        //The start indexes has a special last entry, which is the pathLen + 1 to simplify code in matchers.
        final int[] segmentStartIndexes = new int[cnt + 1];
        segmentStartIndexes[0] = 1;
        cnt = 1;
        for (int i = 1; i < pathLen; i++) {
            if (path[i] == '/')
                segmentStartIndexes[cnt++] = i + 1;
        }
        segmentStartIndexes[cnt] = pathLen + 1;

        return new RouteRequest(path, pathLen, segmentStartIndexes, cnt);
    }

    private static void validWildCardOrNotAWildCard(final String pathTemplate) {
        final int idx = pathTemplate.indexOf('*');
        if (idx == -1)
            return;

        if (idx != (pathTemplate.length() - 1))
            throw new IllegalArgumentException("Wild cards are only supported at the end of a template path");
    }

    /**
     * Parses the URL path - with optional path parameters - into a template.
     *
     * @param <T>          Target type.
     * @param pathTemplate The path template.
     * @param target       The target.
     *
     * @return The template.
     */
    public static <T> Template<T> parseTemplate(final String pathTemplate, final T target) {
        if (pathTemplate == null || target == null)
            throw new IllegalArgumentException();
        validWildCardOrNotAWildCard(pathTemplate);

        // Work directly with char array for performance reasons.  We  ensure that the path starts with a '/'.
        final char[] template = pathWithForwardSlash(pathTemplate, pathTemplate.length());

        final int segmentCount = countSegments(template);

        int len = template.length;

        final List<TemplateSegment> segments = new ArrayList<>(segmentCount);
        int start = 1, segmentIdx = 0;
        TemplateSegment segment;
        boolean wildCard = false;
        for (int i = 2; i <= len; i++) {
            if (i == len || template[i] == '/') {
                segment = createSegment(segmentIdx++, template, start, i);
                wildCard = wildCard || (segment instanceof TemplateWildCardSegment);
                segments.add(segment);
                start = i + 1;
            }
        }
        return new Template<>(
                pathTemplate,
                Collections.unmodifiableList(segments),
                wildCard,
                target
        );
    }

    /**
     * Creates a new router instance based on the specified templates and default target.
     *
     * @param <T>           Target type.
     * @param templates     The templates.
     * @param defaultTarget The default target.
     *
     * @return The router.
     */
    public static <T> Router<T> createRouter(
            final Set<? extends Template<? extends Supplier<? extends T>>> templates,
            final T defaultTarget
    ) {
        return new RouterFactory<>(templates, defaultTarget).create();
    }
}
