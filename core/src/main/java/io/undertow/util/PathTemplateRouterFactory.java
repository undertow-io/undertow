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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link PathTemplateRouterFactory} is a utility that provides classes and methods for creating routers that route URL paths
 * based on URL path templates.
 *
 * URL path templates are parsed from strings that:
 * <ul>
 * <li>Start with '/'.</li>
 * <li>Contain 0 or more segments, delimited by '/' characters. The following 3 segment types are supported:
 * <ol>
 * <li>Static strings. The string '/abc/{varNameA}/def' is a valid URL path template that contains the static string 'abc' as
 * its first segment and the static string 'def' as its third segment.</li>
 * <li>Named parameters. The string '/abc/{varNameA}/def' is a valid URL path template that contains the named parameter
 * 'varNameA' as its second segment.</li>
 * <li>Wildcards. The string '/abc/{varNameA}/*' is a valid URL path template that contains a wildcard as its third segment.
 * Wildcards may be prefixed, i.e. the string '/abc/{varNameA}/def*' is also a valid URL path template.</li>
 * </ol>
 * </li>
 * <li>Contain at most one wildcard. If a wildcard is present, it must be the last character in the template string.</li>
 * </ul>
 *
 * The builders and factories that are encapsulated by this class are not thread-safe, but the resulting routers are
 * thread-safe.
 *
 * @author Dirk Roets
 */
public class PathTemplateRouterFactory {

    //<editor-fold defaultstate="collapsed" desc="Design and implementation notes">
    /*
    Design Decisions

    The objective of this factory is to create instances of PathTemplateRouter that are thread-safe and very fast, even
    (to an extent) at the expense of maintainable code.  The following design decisions are worth noting:

        1.  It is assumed that most services that use routing based on path templates will setup a router once (when the
            service starts) using a single thread and will then use that router to route millions of inbound requests
            using multiple concurrent threads. Perhaps the setup will not happen exactly once, but the mutation of routes
            happen very few times when compared to the number of times that requests are routed.  For this reason this factory
            is heavily biased towards optimising the performance of routing requests - sometimes at the expense of the
            performance of mutating routes.

        2.  Taking point (1) above into consideration. Documentation and comments refer to two distinct phases of processing:

            2.1 "Setup phase" as the phase/process during which routes are mutated and a router instance is created.
            2.2 "Routing phase" as the phase/process during which an existing router is used to route requests.

        2.  The matcher- and router- classes that are encapsulated inside of this factory do not implement unnecessary checks
            during the routing phase. The objective is to avoid the associated overhead by relying on guarantees made by the
            factory during the setup phase.

        3.  The router classes create instances of RouteRequest that are passed to their underlying matcher instances:

            3.1 RouteRequest contains arrays that are read directly - without being copied - by the matcher classes. It is
                therefore technically possible to mutate instances of RouteRequest during calls to Matcher.match, but the overall
                design and integrity of the router classes depend on RouteRequest instances NOT being mutated after creation. This
                limitation on the matcher implementations are considered a key design decision and must be strictly adhered to.
            3.2 Instances of RouteRequest are created by calls to PathTemplateRouter.Route, used during the processing of those
                method calls and then discarded before those method calls return. RouteRequest instances are therefore only ever
                used by a single thread and need not be thread-safe / synchronised.

    Implementation Notes

    This factory encapsulates the following classes:

        1.  RouterFactory (Setup phase). A short lived delegate that accepts a set of parsed URL path templates with a default
            target and that is concerned with validating the templates and creating the fastest available routing strategy
            based on the combination of templates.

        2.  Builder & SimpleBuilder (Setup phase). Provided for convenience to allow developers to build routers by stringing
            together calls - in a typical builder pattern - that add URL path templates with their respective targets for the
            resulting routers. These builders also expose convenience methods to parse URL path templates provided as strings
            and to wrap targets inside of suppliers for compatibility with RouterFactory.

        3.  Template (Setup phase). URL path template strings are structurally validated and then parsed into instances of
            Template. Instances of Template are wrapped inside of PathTemplatePatternEqualsAdapter which allows duplicate
            template patterns to be detected. For example, '/abc/{varNameA}/def' and '/abc/{varNameB}/def' are different
            templates, but represent the same pattern of: ['abc'],[named variable],['def']. It does not make sense to allow
            multiple templates that have the same pattern to be added to the same router as all requested paths that match the
            first template will also match the second template and there is no predictable way to determine which is the better
            match. Template strings are eagerly parsed into Templates and eagerly checked for uniqueness of their patterns by
            builders. The objective there is to fail fast - with an exception - as soon as a duplicate pattern is added. This
            approach will make it easier for developers to know which pattern is duplicated and where it is duplicated in their
            code. Templates consist of 0 or more segments with each segment having a fixed position inside of the template.
            3 different types of segments are supported by templates, each represented by its own class. These instances are
            recognised by the RouterFactory that then generates matchers that will be used to recognise matches during the
            routing phase.

        4.  Template segments (Multiple classes. Setup phase). Represents the three different types of segments that appear at
            specific positions inside of templates.

        5.  MatcherLeafArterfacts (Setup phase). An intermediary class used by the RouterFactory to structure template segments
            as leaves of trees before finally creating the routers.

        6.  Routers (Multiple classes. Routing phase). Routes requested URL paths to the most specific URL path template and
            target that can be considered a match for the path. See the sections below for more details on routing
            methodology and routing optimisation.

        7.  Matchers (Multiple classes. Routing phase). Matchers are members of a strategy pattern that enables the
            RouterFactory to compose highly optimised strategies for determining the most specific URL path templates that
            'match' requested URL paths. There are essentially 3 different types of matcher implementations.

            7.1 Segment matchers. These matchers determine if a segment (or part of a segment) in a requested URL path is a
                match for the associated segment (same position) of a URL path template.
            7.2 Composite matchers. These matchers combine multiple segment matchers into one matcher and uses a strategy -
                specific to the matcher implementation - to find underlying matchers that match specific segments.
            7.3 Terminal matchers. These matchers are added as the final leaves in the trees and are tasked with instantiating
                the PathTemplateRouteResult instances - populated where applicable with matched path parameters.

    Routing methodology

        *   Routers can route requested URL paths to any number of underlying URL path templates, provided that the patterns
            for all URL path templates are unique.  Specifically the template strings '/abc/{varNameA}/def' and
            '/abc/{varNameB}/def' uses different parameter names, but the patterns are the same and a router can therefore
            only contain one of those templates.

        *   Templates consist of segments in numbered positions and routers recognise 3 types of segments, namely: static
            segments, parameter segments and wildcard segments.

        *   When multiple underlying URL path templates match a requested URL path, then the router picks the most specific
            template that matches the path.  Another way of looking at "most specific" is that it is the template that is the
            least likely to match if something in the requested URL path were to change.  For example:

            *   The templates '/abc/def/ghi' and '/abc/{varA}/ghi' will both match the request '/abc/def/ghi' and in this case
                the routers will return the first template. Templates that contain more static segments are always more
                specific than templates that contain less static segments.
            *   The templates '/abc/{varA}/ghi' and '/abc/{varA}/{varB}' will both match the request '/abc/def/ghi' and in this
                case the routers will return the first template. This first template is once again more specific as it contains
                more static segments.
            *   The templates '/abc/def/{varA}' and '/abc/{varB}/ghi' will both match the request '/abc/def/ghi' and in this
                case the routers will return the first template. Templates with static segments that appear earlier in the
                templates are more specific than templates with static segments that appear later in the templates.
            *   The templates '/abc/{varA}' and '/abc/*' will both match the request '/abc/def' and in this case the routers
                will return the first template. Templates that contain wildcards are always less specific than templates that
                do not contain wildcards.
            *   The templates '/abc/*' and '/abc*' will both match the request '/abc/def' and in this case the rotuers will
                return the first template. The first template contains more static segments and is therefore considered
                more specific.
            *   The templates '/abc*' and '/*' will both match the request '/abc/def' and in this case the rotuers will
                return the first template. Wildcards that contain prefixes are considered to e more specific than wilcards
                that do not contain prefixes.

    Routing optimisation

        *   The performance during the routing phase is optimised in the following ways:

            *   Optimising the performance of matching individual segments in URL path templates. This is mostly down to
                writing basic code that avoids duplicating the same checks and that is efficent from a memory and thread-safety
                point of view. This is fairly simple.
            *   Using tree structures to eliminate unnecessary/duplicate matching of segments.  For example if it has been
                determined that the requested URL path '/abc/def' does not match the template '/ghi/jkl' because static segment
                number one is a mismatch, then there is no need to check if it matches the template '/ghi/mno' as we have
                already determined that the static segment 'ghi' is not matched. These templates can be represented as a tree:
                    ghi
                    |- jkl
                    |- mno
                If 'ghi' is not matched then there is no need to check any of its child leaves etc.
            *   Eliminating entire trees based on the number of segments in the templates (depth of the trees) and the
                number of segments in the requested URL paths. For example, it is not necessary to ever check if the template
                '/abc/def/ghi' is a match for the requested URL path '/abc/def' purely based on the mismatch of the number
                of segments present in the template vs the request. For this reason trees are kept as arrays that underly
                the routers and the trees are indexed by the number of segments present inside of the trees. This provides
                a very efficient way to immediately illiminate large number of templates that could never match requests.
            *   Provding a specialised routing strategy for templates that contain wildcards. This allows routing for
                templates that do not contain wildcards to eliminate trees that have both too few and too many segments
                from searches whilst still allowing templates that contain wildcards to search through templates that contain
                fewer segments than the requested URL path.
            *   Implementing a binary search across 3 or more leaves when those leaves are all static segments with a common
                parent leaf.

     */
    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Template segment inner classes">
    /**
     * Parent class for all segments inside a parsed URL path template ({@link Template}).
     *
     * For the purposes of this class a segment is defined as the parts of a URL path template that are delimited by '/'
     * characters. The following types of segments are implemented as extensions of this class:
     * <ul>
     * <li>{@link StaticTemplateSegment} that represents a static string that must be exactly equal to the segment in the same
     * position of a requested path in order for the segment to be considered a 'match' for the request.</li>
     * <li>{@link ParamTemplateSegment} that represents a named parameter that will match any segment in the same position of a
     * requested path.</li>
     * <li>{@link WildCardTemplateSegment} that represents a wildcard that will match any content (for any number of segments)
     * that follows the '*' wildcard character.</li>
     * </ul>
     *
     * Extensions of this class must be immutable.
     */
    private abstract static class AbstractTemplateSegment implements PathTemplatePattern {

        /**
         * Index for this segment in the array of segments for the template.
         */
        protected final int segmentIdx;

        private AbstractTemplateSegment(final int segmentIdx) {
            this.segmentIdx = segmentIdx;

            if (segmentIdx < 0) { //0-based index for an array
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * A static string that must be exactly equal to the segment in the same position of a requested path in order for the
     * segment to be considered a 'match' for the request.
     *
     * Instances of this class are immutable.
     */
    private static class StaticTemplateSegment extends AbstractTemplateSegment {

        private final String value;

        private StaticTemplateSegment(
                final int segmentIdx,
                final String value
        ) {
            super(segmentIdx);
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public String toString() {
            return "StaticTemplateSegment{" + "segmentIdx=" + segmentIdx + ", value=" + value + '}';
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
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StaticTemplateSegment other = (StaticTemplateSegment) obj;
            if (this.segmentIdx != other.segmentIdx) {
                return false;
            }
            return Objects.equals(this.value, other.value);
        }

        @Override
        public int patternHashCode() {
            return hashCode();
        }

        @Override
        public boolean patternEquals(final PathTemplatePattern obj) {
            return equals(obj);
        }
    }

    /**
     * A named parameter that will match any segment in the same position of a requested path.
     *
     * Instances of this class are immutable.
     */
    private static class ParamTemplateSegment extends AbstractTemplateSegment {

        /**
         * Name for the parameter, excluding the '{' and '}' braces.
         */
        private final String paramName;

        private ParamTemplateSegment(
                final int segmentIdx,
                final String paramName
        ) {
            super(segmentIdx);
            this.paramName = Objects.requireNonNull(paramName);
        }

        @Override
        public String toString() {
            return "ParamTemplateSegment{" + "segmentIdx=" + segmentIdx + ", paramName=" + paramName + '}';
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
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ParamTemplateSegment other = (ParamTemplateSegment) obj;
            if (this.segmentIdx != other.segmentIdx) {
                return false;
            }
            return Objects.equals(this.paramName, other.paramName);
        }

        @Override
        public int patternHashCode() {
            int hash = 5;
            hash = 53 * hash + this.segmentIdx;
            return hash;
        }

        @Override
        public boolean patternEquals(final PathTemplatePattern obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ParamTemplateSegment other = (ParamTemplateSegment) obj;
            return this.segmentIdx == other.segmentIdx;
        }
    }

    /**
     * A wildcard that will match any content (for any number of segments) that follows the '*' wildcard character.
     *
     * Instances of this class are immutable.
     */
    private static class WildCardTemplateSegment extends AbstractTemplateSegment {

        /**
         * Prefix for the wild card, excluding the '*' itself. May be an empty string.
         */
        private final String prefix;

        private WildCardTemplateSegment(
                final int segmentIdx,
                final String prefix
        ) {
            super(segmentIdx);
            this.prefix = Objects.requireNonNull(prefix);
        }

        @Override
        public String toString() {
            return "WildCardTemplateSegment{" + "segmentIdx=" + segmentIdx + ", prefix=" + prefix + '}';
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
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final WildCardTemplateSegment other = (WildCardTemplateSegment) obj;
            if (this.segmentIdx != other.segmentIdx) {
                return false;
            }
            return Objects.equals(this.prefix, other.prefix);
        }

        @Override
        public int patternHashCode() {
            return hashCode();
        }

        @Override
        public boolean patternEquals(final PathTemplatePattern obj) {
            return equals(obj);
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Template inner class">
    /**
     * A Template is a parsed sequence of segments - in specific positions - for a URL path template.
     *
     * @param <T> Target type.
     */
    public static class Template<T> implements PathTemplatePattern {

        private final String pathTemplate;
        private final List<AbstractTemplateSegment> segments;
        private final T target;
        private final boolean wildCard;

        private Template(
                final String pathTemplate,
                final List<AbstractTemplateSegment> segments,
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
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Template<?> other = (Template<?>) obj;
            if (this.wildCard != other.wildCard) {
                return false;
            }
            if (!Objects.equals(this.segments, other.segments)) {
                return false;
            }
            return Objects.equals(this.target, other.target);
        }

        @Override
        public int patternHashCode() {
            int hash = 7;
            for (final AbstractTemplateSegment segment : segments) {
                hash = 97 * hash + segment.patternHashCode();
            }
            return hash;
        }

        @Override
        public boolean patternEquals(final PathTemplatePattern obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final Template<?> other = (Template<?>) obj;
            if (wildCard != other.wildCard) {
                return false;
            }
            if (segments.size() != other.segments.size()) {
                return false;
            }

            final Iterator<AbstractTemplateSegment> it = segments.iterator();
            final Iterator<AbstractTemplateSegment> otherIt = other.segments.iterator();
            while (it.hasNext()) {
                if (!it.next().patternEquals(otherIt.next())) {
                    return false;
                }
            }
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
    /**
     * A parsed representation of a requested URL path.
     *
     * Instances of this class expose (internally) two arrays that can technically be mutated, but that MAY NOT be mutated. This
     * is a key design decision than must be maintained in order to ensure integrity of all router and matcher implementations.
     */
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
    //<editor-fold defaultstate="collapsed" desc="Matcher inner classes">
    /**
     * A strategy pattern that enables the RouterFactory to compose highly optimised strategies for determining the most
     * specific URL path templates that 'match' requested URL paths.
     *
     * Implementations must be immutable and therefore thread-safe.
     *
     * @param <T> Target type.
     */
    @FunctionalInterface
    private interface Matcher<T> {

        /**
         * Attempts to match the specified request using the strategy represented by this matcher instance.
         *
         * @param request The request.
         * @return The result. Calls to these methods return a default object (null object pattern) rather than {@code NULL}
         * when this matcher instanced cannot match the specified request.
         */
        PathTemplaterRouteResult<T> match(RouteRequest request);
    }

    /**
     * A segment matcher that calls the underlying 'nextMatcher' if the associated segment is exactly equal to the value
     * contained by this matcher.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Type of target.
     */
    private static class SegmentEqualsMatcher<T> implements Matcher<T> {

        private final PathTemplaterRouteResult<T> defaultResult;
        private final char[] value;
        private final int len;
        private final int segmentIdx;
        private final Matcher<T> nextMatcher;

        private SegmentEqualsMatcher(
                final PathTemplaterRouteResult<T> defaultResult,
                final char[] value,
                final int segmentIdx,
                final Matcher<T> nextMatcher
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.value = Objects.requireNonNull(value);
            this.len = value.length;
            this.segmentIdx = segmentIdx;
            this.nextMatcher = Objects.requireNonNull(nextMatcher);

            if (this.segmentIdx < 0) { //Index into an array of segments.
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return "SegmentEqualsMatcher{" + "value=" + String.copyValueOf(value) + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;
            if (segmentLength != len) {
                return defaultResult;
            }

            for (int i = 0; i < segmentLength; i++) {
                if (value[i] != request.path[segmentStartIdx + i]) {
                    return defaultResult;
                }
            }

            return nextMatcher.match(request);
        }
    }

    /**
     * A segment matcher that calls the underlying 'nextMatcher' if the associated segment starts with the value contained by
     * this matcher.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SegmentStartsWithMatcher<T> implements Matcher<T> {

        private final PathTemplaterRouteResult<T> defaultResult;
        private final char[] value;
        private final int len;
        private final int segmentIdx;
        private final Matcher<T> nextMatcher;

        private SegmentStartsWithMatcher(
                final PathTemplaterRouteResult<T> defaultResult,
                final char[] value,
                final int segmentIdx,
                final Matcher<T> nextMatcher
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.value = Objects.requireNonNull(value);
            this.len = value.length;
            this.segmentIdx = segmentIdx;
            this.nextMatcher = Objects.requireNonNull(nextMatcher);

            if (this.len < 1) {
                throw new IllegalArgumentException();
            }
            if (this.segmentIdx < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return "SegmentStartsWithMatcher{" + "value=" + String.copyValueOf(value) + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;
            if (segmentLength < len) {
                return defaultResult;
            }

            for (int i = 0; i < len; i++) {
                if (value[i] != request.path[segmentStartIdx + i]) {
                    return defaultResult;
                }
            }
            return nextMatcher.match(request);
        }
    }

    /**
     * A simple composite matcher that tries all of the underlying matchers in the same order that they were provided to the
     * constructor.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Type of target.
     */
    private static class SimpleCompositeMatcher<T> implements Matcher<T> {

        private final PathTemplaterRouteResult<T> defaultResult;
        private final Matcher<T>[] matchers;
        private final int len;

        private SimpleCompositeMatcher(
                final PathTemplaterRouteResult<T> defaultResult,
                final List<? extends Matcher<? extends T>> matchers
        ) {
            this.matchers = arrayOfMatchers(matchers);
            this.len = this.matchers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);

            if (this.len < 2) {
                throw new IllegalArgumentException("Should not use a composite");
            }
        }

        @Override
        public String toString() {
            return "SimpleCompositeMatcher{" + "len=" + len + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            PathTemplaterRouteResult<T> result;
            for (int i = 0; i < len; i++) {
                result = matchers[i].match(request);
                if (result != defaultResult) {
                    return result;
                }
            }
            return defaultResult;
        }
    }

    /**
     * A specialized composite matcher that performs a binary search over an array of unique segment equals matchers to quickly
     * find the matcher that matches the content in the associated segment of the requested URL path.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class BinarySearchCompositeMatcher<T> implements Matcher<T> {

        private final PathTemplaterRouteResult<T> defaultResult;
        private final SegmentEqualsMatcher<T>[] matchers;
        private final int matchersLen;
        private final int segmentIdx;
        private final int minSegmentLength;
        private final int maxSegmentLength;

        private BinarySearchCompositeMatcher(
                final PathTemplaterRouteResult<T> defaultResult,
                final List<? extends SegmentEqualsMatcher<? extends T>> matchers
        ) {
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.matchers = sortedSegmentEqualsMatchers(matchers);
            this.matchersLen = this.matchers.length;

            if (this.matchersLen == 0) {
                throw new IllegalArgumentException();
            }

            this.segmentIdx = this.matchers[0].segmentIdx;

            int i = 0;
            for (final SegmentEqualsMatcher<?> m : this.matchers) {
                if (m.len > i) {
                    i = m.len;
                }
            }
            this.maxSegmentLength = i;
            for (final SegmentEqualsMatcher<?> m : this.matchers) {
                if (m.len < i) {
                    i = m.len;
                }
            }
            this.minSegmentLength = i;
        }

        @Override
        public String toString() {
            return "BinarySearchCompositeMatcher{" + "matchersLen=" + matchersLen + '}';
        }

        private static int compare(final char[] c1, final char[] c2, final int len) {
            int result;
            for (int i = 0; i < len; i++) {
                result = c1[i] - c2[i];
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

        private static int compare(final SegmentEqualsMatcher<?> s1, final SegmentEqualsMatcher<?> s2) {
            int result = Integer.compare(s1.len, s2.len);
            if (result != 0) {
                return result;
            }

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
            for (final SegmentEqualsMatcher<? extends T> matcher : matchers) {
                result[idx++] = matcher;
            }

            Arrays.sort(result, BinarySearchCompositeMatcher::compare);
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
            if (requestSegmentLength < matcher.len) {
                return -1;
            }
            if (requestSegmentLength > matcher.len) {
                return 1;
            }

            int result;
            for (int i = 0; i < requestSegmentLength; i++) {
                result = requestPath[requestSegmentStartIdx + i] - matcher.value[i];
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            final int segmentStartIdx = request.segmentStartIndexes[segmentIdx];

            //Index immediately after the last character of the segment.
            final int segmentEndIdx = request.segmentStartIndexes[segmentIdx + 1] - 1;

            final int segmentLength = segmentEndIdx - segmentStartIdx;

            //We don't evaluate the static matchers if none of them has the correct length to match this segment anyway
            if (segmentLength < this.minSegmentLength || segmentLength > this.maxSegmentLength) {
                return defaultResult;
            }

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
                    final PathTemplaterRouteResult<T> result = midVal.nextMatcher.match(request);
                    return result;
                }
            }
            return defaultResult;
        }
    }

    /**
     * A terminal matcher for simple results that do not contain parameters nor wildcards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SimpleTerminalMatcher<T> implements Matcher<T> {

        private final PathTemplaterRouteResult<T> result;

        private SimpleTerminalMatcher(
                final PathTemplaterRouteResult<T> result
        ) {
            this.result = Objects.requireNonNull(result);
        }

        @Override
        public String toString() {
            return "SimpleTerminalMatcher{" + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            if (result.getPathTemplate().isPresent()) {
                /*  This will be set in the exchange, meaning some developers may be tempted to mutate it. For
                    backwards compatibility sake, a new mutable map is created. */
                final Map<String, String> paramValues = new HashMap<>(result.getParameters());
                return new PathTemplaterRouteResult<>(result.getTarget(), result.getPathTemplate(), paramValues);
            }
            return result;
        }
    }

    /**
     * A terminal matcher for results that contain parameters, but that contain no wildcards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class ParameterisedTerminalMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int[] paramSegmentIndexes;
        private final String[] paramNames;
        private final int paramCount;
        private final int paramMapSize;

        private ParameterisedTerminalMatcher(
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

            if (this.paramCount != this.paramNames.length) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return "ParameterisedTerminalMatcher{" + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            return new PathTemplaterRouteResult<>(
                    target,
                    pathTemplate,
                    createParams(paramSegmentIndexes, paramNames, paramCount, paramMapSize, request)
            );
        }
    }

    /**
     * A terminal matcher for results that contain a wildcard, but that contains no other parameters.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SimpleWildcardTerminalMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int segmentIdx;
        private final int prefixLength;

        private SimpleWildcardTerminalMatcher(
                final T target,
                final Optional<String> pathTemplate,
                final int segmentIdx,
                final int prefixLength
        ) {
            this.target = Objects.requireNonNull(target);
            this.pathTemplate = Objects.requireNonNull(pathTemplate);
            this.segmentIdx = segmentIdx;
            this.prefixLength = prefixLength;

            if (segmentIdx < 0) {
                throw new IllegalArgumentException();
            }
            if (prefixLength < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return "SimpleWildcardTerminalMatcher{" + '}';
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            final int startIdx = request.segmentStartIndexes[segmentIdx] + prefixLength;
            if (startIdx == (request.pathLen - 1)) {
                return new PathTemplaterRouteResult<>(
                        target,
                        pathTemplate,
                        createParams("*", "")
                );
            }

            final String wildCardValue = new String(request.path, startIdx, request.pathLen - startIdx);

            return new PathTemplaterRouteResult<>(
                    target,
                    pathTemplate,
                    createParams("*", wildCardValue)
            );
        }
    }

    /**
     * A terminal matcher for results that contain both parameters and wildcards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class ParameterisedWildcardTerminalMatcher<T> implements Matcher<T> {

        private final T target;
        private final Optional<String> pathTemplate;
        private final int[] paramSegmentIndexes;
        private final String[] paramNames;
        private final int paramCount;
        private final int paramMapSize;
        private final int segmentIdx;
        private final int prefixLength;

        private ParameterisedWildcardTerminalMatcher(
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

            if (segmentIdx < 0) {
                throw new IllegalArgumentException();
            }
            if (prefixLength < 0) {
                throw new IllegalArgumentException();
            }
            if (paramCount != this.paramNames.length) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return "ParameterisedWildcardTerminalMatcher{" + '}';
        }

        private Map<String, String> createParams(
                final RouteRequest request,
                final String wildCardValue
        ) {
            final Map<String, String> result = PathTemplateRouterFactory.createParams(
                    paramSegmentIndexes, paramNames, paramCount, paramMapSize, request
            );
            result.put("*", wildCardValue);
            return result;
        }

        @Override
        public PathTemplaterRouteResult<T> match(final RouteRequest request) {
            final int startIdx = request.segmentStartIndexes[segmentIdx] + prefixLength;
            if (startIdx == (request.pathLen - 1)) {
                return new PathTemplaterRouteResult<>(
                        target,
                        pathTemplate,
                        createParams(request, "")
                );
            }

            final String wildCardValue = new String(request.path, startIdx, request.pathLen - startIdx);

            return new PathTemplaterRouteResult<>(
                    target,
                    pathTemplate,
                    createParams(request, wildCardValue)
            );
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Router inner classes">
    /**
     * Parent class - that defines an additional 'route' method - for all routers encapsulated inside PathTemplateRouterFactory.
     *
     * @param <T> Target type.
     */
    private abstract static class AbstractRouter<T> implements PathTemplateRouter<T> {

        /**
         * @return The minimum number (included) of segments that a requested URL path must contain in order to be potentially
         * routable by this router. No attempts will be made to route requested URL paths with fewer segments.
         */
        protected abstract int getMinSegmentCount();

        /**
         * @return The maximum number (included) of segments that a requested URL path must contain in order to be potentially
         * routable by this router. No attempts will be made to route requested URL paths with more segments.
         */
        protected abstract int getMaxSegmentCount();

        /**
         * Routes the request.
         *
         * @param request The request.
         *
         * @return The routing result.
         */
        protected abstract PathTemplaterRouteResult<T> route(RouteRequest request);
    }

    /**
     * A simple router for templates containing combinations of static segments and parameters. This router DOES NOT support
     * wildcards as it contains an optimisation that eliminates trees of templates based on those templates not having the exact
     * number of segments that the requested URL paths have. That logic does not support templates with a variable number of
     * segments, like templates containing wildcards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class SimpleRouter<T> extends AbstractRouter<T> {

        private final int minSegmentCount;
        private final int maxSegmentCount;
        /*  This is a - potentially - sparse array.  Each element represents the matcher for a path with the same
            number of segments as the index of the array.  So the matcher in position 1 in the array is only applicable
            to paths with one segment and so on.  This makes it easy to quickly eliminate a large number of trees (matchers)
            as those will never be able to match the path anyway. */
        private final Matcher<T>[] matchers;
        private final PathTemplaterRouteResult<T> defaultResult;

        private SimpleRouter(
                final Matcher<T>[] matchers,
                final PathTemplaterRouteResult<T> defaultResult
        ) {
            this.matchers = Objects.requireNonNull(matchers);
            this.defaultResult = Objects.requireNonNull(defaultResult);

            this.minSegmentCount = PathTemplateRouterFactory.getMinSegmentCount(matchers);
            this.maxSegmentCount = this.matchers.length - 1;
        }

        @Override
        public String toString() {
            return "SimpleRouter{" + '}';
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.getTarget();
        }

        @Override
        protected int getMinSegmentCount() {
            return minSegmentCount;
        }

        @Override
        protected int getMaxSegmentCount() {
            return maxSegmentCount;
        }

        @Override
        protected PathTemplaterRouteResult<T> route(final RouteRequest request) {
            /*  Optimisation. Early exit when the request factory determined that we don't have a matcher with the
                resulting number of segments. */
            if (request == CANT_MATCH_REQUEST) {
                return defaultResult;
            }

            /*  Optimisation. Only search the tree that has exactly the same number of segments as the requested URL path
                template. */
            final Matcher<T> matcher = matchers[request.segmentCount];
            return matcher != null ? matcher.match(request) : defaultResult;
        }

        @Override
        public PathTemplaterRouteResult<T> route(final String path) {
            //This router is empty.
            if (maxSegmentCount == 0) {
                return defaultResult;
            }

            final RouteRequest request = createRouteRequest(path, minSegmentCount, maxSegmentCount);
            return route(request);
        }
    }

    /**
     * A router for templates that contain wildcards. Unlike {@link SimpleRouter}, this router only eliminates trees that
     * contain more segments than the requested URL path as trees with less segments can still match the requested URL paths
     * through their wildcards.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class WildCardRouter<T> extends AbstractRouter<T> {

        private final int minSegmentCount;
        /*  This is a - potentially - sparse array.  Each element represents the matcher for a path with the same
            number of segments as the index of the array.  So the matcher in position 1 in the array is only applicable
            to paths with one or more segments and so on.  This makes it easy to quickly eliminate trees with more segments
            than that of the requested URL path. */
        private final Matcher<T>[] matchers;
        private final int len;
        private final PathTemplaterRouteResult<T> defaultResult;

        private WildCardRouter(
                final Matcher<T>[] matchers,
                final PathTemplaterRouteResult<T> defaultResult
        ) {
            this.matchers = Objects.requireNonNull(matchers);
            this.len = this.matchers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);

            this.minSegmentCount = PathTemplateRouterFactory.getMinSegmentCount(matchers);
        }

        @Override
        public String toString() {
            return "WildCardRouter{" + '}';
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.getTarget();
        }

        @Override
        protected int getMinSegmentCount() {
            return minSegmentCount;
        }

        @Override
        protected int getMaxSegmentCount() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected PathTemplaterRouteResult<T> route(final RouteRequest request) {
            /*  Optimisation. Early exit when the request factory determined that we don't have a matcher with the
                resulting number of segments. */
            if (request == CANT_MATCH_REQUEST) {
                return defaultResult;
            }

            /*  Optimisation. Only search the trees that contain at most the same number of segments as the requested URL
                path as templates with more segments will never be able to match. Trees are searched in descending order
                based on the number of segments in the templates, therefore templates with more segments will be considered
                to be more specific and therefore more likely to match. */
            Matcher<T> matcher;
            PathTemplaterRouteResult<T> result;
            for (int i = Math.min(len - 1, request.segmentCount); i > 0; i--) {
                matcher = matchers[i];
                if (matcher != null) {
                    result = matcher.match(request);
                    if (result != defaultResult) {
                        return result;
                    }
                }
            }
            return defaultResult;
        }

        @Override
        public PathTemplaterRouteResult<T> route(final String path) {
            final RouteRequest request = createRouteRequest(path, minSegmentCount, Integer.MAX_VALUE);
            return route(request);
        }
    }

    /**
     * A composite router that attempts to route a path by using the underlying routers in the same order that the routers were
     * provided to the constructor.
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    private static class CompositeRouter<T> extends AbstractRouter<T> {

        private final AbstractRouter<T>[] routers;
        private final int len;
        private final PathTemplaterRouteResult<T> defaultResult;
        //
        private final int minSegmentCount;
        private final int maxSegmentCount;

        private CompositeRouter(
                final AbstractRouter<T>[] routers,
                final PathTemplaterRouteResult<T> defaultResult
        ) {
            this.routers = Objects.requireNonNull(routers);
            this.len = this.routers.length;
            this.defaultResult = Objects.requireNonNull(defaultResult);
            this.minSegmentCount = getMinSegmentCount(routers);
            this.maxSegmentCount = getMaxSegmentCount(routers);
        }

        private static int getMinSegmentCount(AbstractRouter<?>[] routers) {
            int result = Integer.MAX_VALUE;
            for (final AbstractRouter<?> router : routers) {
                if (router.getMinSegmentCount() < result) {
                    result = router.getMinSegmentCount();
                }
            }
            return result;
        }

        private static int getMaxSegmentCount(AbstractRouter<?>[] routers) {
            int result = 0;
            for (final AbstractRouter<?> router : routers) {
                if (router.getMaxSegmentCount() > result) {
                    result = router.getMaxSegmentCount();
                }
            }
            return result;
        }

        @Override
        public T getDefaultTarget() {
            return defaultResult.getTarget();
        }

        @Override
        protected int getMinSegmentCount() {
            return minSegmentCount;
        }

        @Override
        protected int getMaxSegmentCount() {
            return maxSegmentCount;
        }

        @Override
        protected PathTemplaterRouteResult<T> route(final RouteRequest request) {
            PathTemplaterRouteResult<T> result;
            for (int i = 0; i < len; i++) {
                //Check that the next router supports the request based on the number of segments.
                if (request.segmentCount < routers[i].getMinSegmentCount()
                        || request.segmentCount > routers[i].getMaxSegmentCount()) {
                    continue;
                }

                result = routers[i].route(request);
                if (result != defaultResult) {
                    return result;
                }
            }
            return defaultResult;
        }

        @Override
        public PathTemplaterRouteResult<T> route(final String path) {
            final RouteRequest request = createRouteRequest(path, minSegmentCount, maxSegmentCount);
            return route(request);
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="MatcherLeafArterfacts inner class">
    /**
     * An intermediary class used by the RouterFactory to structure template segments as leaves of trees before finally creating
     * the routers.
     *
     * Instances of this class ARE NOT thread-safe.
     *
     * @param <T> Target type.
     */
    private static class MatcherLeafArterfacts<T> {

        private final Map<PathTemplatePatternEqualsAdapter<AbstractTemplateSegment>, MatcherLeafArterfacts<T>> children
                = new HashMap<>();
        /**
         * Index of the segment represented by these artefacts.
         */
        private int segmentIdx;
        /**
         * The template with which this leave is associated.
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
        private StaticTemplateSegment staticSegment;
        /**
         * IF this leave is a parameter segment leave: The index of the parameter in the array of parameters for the template.
         */
        private int paramIdx = -1;
        /**
         * IF this leave is a parameter segment leave AND it is the final leave: Reference to the array of segment indexes that
         * are parameter segments in the current template.
         */
        private List<Integer> paramIndexes;
        /**
         * IF this leave is a parameter segment leave AND it is the final leave: Reference to the array of names for parameters
         * in the template.
         */
        private List<String> paramNames;
        /**
         * If this leave is a wild card segment: The wild card segment for this leave.
         */
        private WildCardTemplateSegment wildCardSegment;
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RouterFactory inner class">
    private static class RouterFactory<T> {

        // Input.
        private final Set<? extends Template<? extends Supplier<? extends T>>> templates;
        private final T defaultTarget;
        // Results and master temporary structures..
        private PathTemplaterRouteResult<T> defaultResult;
        private int maxSegmentCount;
        private int maxWildCardSegmentCount;
        private MatcherLeafArterfacts[] rootLeaves;
        private MatcherLeafArterfacts[] wildCardRootLeaves;
        // Temporary structures used per call to processTemplate(Template).
        private Template<? extends Supplier<? extends T>> currentTemplate;
        private int currentSegmentCount;
        // Temporary structures used per call to processSegment(TemplateSegment).
        private List<AbstractTemplateSegment> segmentStack;
        private List<Integer> currentParamIndexes;
        private List<String> currentParamNames;
        private MatcherLeafArterfacts<Supplier<? extends T>> currentLeave;
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

        private static <T> PathTemplaterRouteResult<T> createDefaultResult(final T defaultTarget) {
            return new PathTemplaterRouteResult<>(
                    defaultTarget,
                    Optional.empty(),
                    Collections.emptyMap()
            );
        }

        @SuppressWarnings("unchecked")
        private static <A> Matcher<A>[] toMatcherArray(final Collection<? extends Matcher<A>> matchers) {
            final Matcher[] result = new Matcher[matchers.size()];
            int idx = 0;
            for (final Matcher<?> m : matchers) {
                result[idx++] = m;
            }

            return result;
        }

        private SimpleRouter<T> createEmptyRouter() {
            /*  An immutable map here is okay, seeing that the PathTemplateMatch won't be set in the
                exchange and therefore no attempts can be made to mutate the map outside of the internal
                workings of the path template router and handlers. */
            final PathTemplaterRouteResult<T> routeResult = new PathTemplaterRouteResult<>(
                    defaultTarget, Optional.empty(), Map.of()
            );
            final Matcher<T> innerMatchers = new SimpleTerminalMatcher<>(
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
                    if (cnt > maxWildCardSegmentCount) {
                        maxWildCardSegmentCount = cnt;
                    }
                } else {
                    if (cnt > maxSegmentCount) {
                        maxSegmentCount = cnt;
                    }
                }
            }
        }

        private void initRootLeaves() {
            this.rootLeaves = new MatcherLeafArterfacts[maxSegmentCount + 1];
            this.wildCardRootLeaves = new MatcherLeafArterfacts[maxWildCardSegmentCount + 1];
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
        private MatcherLeafArterfacts<Supplier<? extends T>> getCurrentRootLeave() {
            final MatcherLeafArterfacts[] artefacts = (currentTemplate.wildCard)
                    ? wildCardRootLeaves : rootLeaves;

            MatcherLeafArterfacts result = artefacts[currentSegmentCount];
            if (result != null) {
                return result;
            }

            result = new MatcherLeafArterfacts();
            result.segmentIdx = -1;
            result.rootLeave = true;
            artefacts[currentSegmentCount] = result;
            return result;
        }

        private void processSegment(final AbstractTemplateSegment segment) {
            segmentStack.add(segment);

            final PathTemplatePatternEqualsAdapter<AbstractTemplateSegment> adapter = new PathTemplatePatternEqualsAdapter<>(segment);
            MatcherLeafArterfacts<Supplier<? extends T>> nextLeave = currentLeave.children.get(adapter);
            if (nextLeave == null) {
                nextLeave = new MatcherLeafArterfacts<>();
                nextLeave.segmentIdx = segment.segmentIdx;
                currentLeave.children.put(adapter, nextLeave);
            }

            currentLeave = nextLeave;
            if (segment instanceof StaticTemplateSegment) {
                final StaticTemplateSegment staticSegment = (StaticTemplateSegment) segment;
                currentLeave.staticSegment = staticSegment;
            } else if (segment instanceof ParamTemplateSegment) {
                final ParamTemplateSegment paramSegment = (ParamTemplateSegment) segment;
                currentLeave.paramIdx = currentParamNames.size();
                currentParamIndexes.add(paramSegment.segmentIdx);
                currentParamNames.add(paramSegment.paramName);
            } else if (segment instanceof WildCardTemplateSegment) {
                final WildCardTemplateSegment wildCardSegment = (WildCardTemplateSegment) segment;
                if (segmentStack.size() != currentSegmentCount) {
                    throw new IllegalArgumentException("Wild cards are only supported at the end of a template path");
                }
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
                final MatcherLeafArterfacts<?> artefacts
        ) {
            System.out.println(
                    ident(indent) + "Node (" + (artefacts.staticSegment != null ? artefacts.staticSegment.value : artefacts.paramIdx) + ")");
            for (final MatcherLeafArterfacts<?> child : artefacts.children.values()) {
                printNode(indent + 1, child);
            }
        }

        private void printNodes() {
            System.out.println("Nodes:");
            for (int i = 0; i < maxSegmentCount + 1; i++) {
                if (rootLeaves[i] != null) {
                    printNode(0, rootLeaves[i]);
                }
            }
            System.out.println();
            System.out.println("Wild Card Nodes:");
            for (int i = 0; i < maxWildCardSegmentCount + 1; i++) {
                if (rootLeaves[i] != null) {
                    printNode(0, wildCardRootLeaves[i]);
                }
            }
        }

        private void initMatchers() {
            this.matchers = new Matcher[maxSegmentCount + 1];
            this.wildCardMatchers = new Matcher[maxWildCardSegmentCount + 1];
        }

        private MatcherLeafArterfacts<Supplier<? extends T>> getLeave(
                final MatcherLeafArterfacts[] leaves,
                final int segmentCount
        ) {
            @SuppressWarnings("unchecked")
            final MatcherLeafArterfacts<Supplier<? extends T>> result = leaves[segmentCount];
            return result;
        }

        private static <T> Matcher<T> createSimpleResultMatcher(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts) {
            final PathTemplaterRouteResult<T> result = new PathTemplaterRouteResult<>(
                    artefacts.template.target.get(),
                    Optional.of(artefacts.template.pathTemplate),
                    /*  This result will be copied with a mutable map by the CreateSimpleResultMatcher, therefore we
                        can start with an immutable map here. */
                    Map.of()
            );
            return new SimpleTerminalMatcher<>(result);
        }

        private static <T> Matcher<T> createResultWithParamsMatcher(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts) {
            final int[] paramSegmentIndexes = new int[artefacts.paramIndexes.size()];
            final int len = paramSegmentIndexes.length;
            final String[] paramNames = new String[len];
            for (int i = 0; i < len; i++) {
                paramSegmentIndexes[i] = artefacts.paramIndexes.get(i);
                paramNames[i] = artefacts.paramNames.get(i);
            }

            return new ParameterisedTerminalMatcher<>(
                    artefacts.template.target.get(),
                    Optional.of(artefacts.template.pathTemplate),
                    paramSegmentIndexes,
                    paramNames
            );
        }

        private static <T> Matcher<T> createWildCardResultMatcher(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts
        ) {
            if (artefacts.paramIndexes.size() == 1) {
                return new SimpleWildcardTerminalMatcher<>(
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
                return new ParameterisedWildcardTerminalMatcher<>(
                        artefacts.template.target.get(),
                        Optional.of(artefacts.template.pathTemplate),
                        paramSegmentIndexes,
                        paramNames,
                        artefacts.wildCardSegment.segmentIdx,
                        artefacts.wildCardSegment.prefix.length()
                );
            }
        }

        private Matcher<T> createFinalMatcher(final MatcherLeafArterfacts<Supplier<? extends T>> artefacts) {
            if (!artefacts.children.isEmpty()) {
                throw new IllegalStateException();
            }

            if (artefacts.paramNames == null) {
                return createSimpleResultMatcher(artefacts);
            }

            if (artefacts.wildCardSegment != null) {
                return createWildCardResultMatcher(artefacts);
            }

            return createResultWithParamsMatcher(artefacts);
        }

        private Matcher<T> createStaticSegmentMatcher(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts,
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
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts,
                final Matcher<T> nextMatcher
        ) {
            if (artefacts.wildCardSegment.prefix.isEmpty()) {
                return nextMatcher;
            }

            return new SegmentStartsWithMatcher<>(
                    defaultResult,
                    artefacts.wildCardSegment.prefix.toCharArray(),
                    artefacts.wildCardSegment.segmentIdx,
                    nextMatcher
            );
        }

        private Matcher<T> createSimpleMatcher(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts,
                final Matcher<T> nextMatcher
        ) {
            if (artefacts.staticSegment != null) {
                return createStaticSegmentMatcher(artefacts, nextMatcher);
            } else if (artefacts.wildCardSegment != null) {
                return createWildCardSegmentMatcher(artefacts, nextMatcher);
            } else if (artefacts.paramIdx != -1) {
                return nextMatcher;
            } else if (artefacts.rootLeave) {
                return nextMatcher;
            }
            throw new IllegalStateException("Should never happen");
        }

        private List<Matcher<T>> createSortedInnerMatchers(
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts
        ) {
            final List<PathTemplatePatternEqualsAdapter<AbstractTemplateSegment>> keys = new ArrayList<>(artefacts.children.keySet());
            Collections.sort(keys, Comparator.comparing(PathTemplatePatternEqualsAdapter::getPattern,
                    PathTemplateRouterFactory::compareMostSpecificToLeastSpecific
            ));

            final List<Matcher<T>> result = new ArrayList<>(keys.size());
            for (final PathTemplatePatternEqualsAdapter<AbstractTemplateSegment> key : keys) {
                result.add(createMatcher(artefacts.children.get(key)));
            }
            return result;
        }

        private static <T> void addSegmentEqualsRouting(
                final PathTemplaterRouteResult<T> defaultResult,
                final int innerSegmentIdx,
                final List<Matcher<T>> sortedInnerMatchers,
                final List<Matcher<T>> target
        ) {
            if (sortedInnerMatchers.isEmpty()) {
                return;
            }

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
                    target.add(new BinarySearchCompositeMatcher<>(
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
            if (sortedInnerMatchers.isEmpty()) {
                return;
            }

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

        private Matcher<T> createRouterMatcher(final MatcherLeafArterfacts<Supplier<? extends T>> artefacts) {
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
                    return new SimpleCompositeMatcher<>(
                            defaultResult,
                            routerMatchers
                    );
            }
        }

        /*  TODO The recursion here can be removed, but it also never goes deeper than the number of segments per path,
            so it should not be completely unreasonable to use recursion. */
        private Matcher<T> createMatcher(final MatcherLeafArterfacts<Supplier<? extends T>> artefacts) {
            //Exit if this is the end of the branch.
            if (artefacts.finalLeave) {
                //This matcher is just the strategy to generate the result.
                final Matcher<T> nextMatcher = createFinalMatcher(artefacts);
                return createSimpleMatcher(artefacts, nextMatcher);
            }

            //See how many children we have.
            final int childrenSize = artefacts.children.size();
            if (childrenSize == 0) {
                throw new IllegalStateException("Should never happen");
            }

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
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts = getLeave(rootLeaves, i);
                if (artefacts != null) {
                    matchers[i] = createMatcher(artefacts);
                }
            }
            for (int i = 0; i <= maxWildCardSegmentCount; i++) {
                final MatcherLeafArterfacts<Supplier<? extends T>> artefacts = getLeave(wildCardRootLeaves, i);
                if (artefacts != null) {
                    wildCardMatchers[i] = createMatcher(artefacts);
                }
            }
        }

        @SuppressWarnings("unchecked")//Always safe based on the internal handling of this array.
        private Matcher<T>[] getMatchers(final Matcher[] matchers) {
            return matchers;
        }

        @SuppressWarnings("unchecked")
        private static <T> AbstractRouter<T>[] toRouterArray(final List<? extends AbstractRouter<? extends T>> routers) {
            final AbstractRouter[] result = new AbstractRouter[routers.size()];
            int idx = 0;
            for (final AbstractRouter<? extends T> router : routers) {
                result[idx++] = router;
            }
            return result;
        }

        /**
         * @return A new router instance base on this factory.
         */
        public PathTemplateRouter<T> create() {
            if (templates.isEmpty()) {
                return createEmptyRouter();
            }

            initDefaultResults();
            initMaxSegmentCounts();
            initRootLeaves();
            initSegmentStack();
            initCurrentParamIndexes();
            initCurrentParamNames();
            templates.forEach(this::processTemplate);
            if (DEBUG) {
                printNodes();
            }

            initMatchers();
            createMatchers();

            final List<AbstractRouter<T>> routers = new LinkedList<>();
            if (maxSegmentCount > 0) {
                routers.add(new SimpleRouter<>(
                        getMatchers(matchers),
                        defaultResult
                ));
            }

            if (maxWildCardSegmentCount > 0) {
                routers.add(new WildCardRouter<>(
                        getMatchers(wildCardMatchers),
                        defaultResult
                ));
            }

            if (routers.size() == 1) {
                return routers.get(0);
            }

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
     * Builder for {@link PathTemplateRouter} instances.
     *
     * Instances of this class ARE NOT thread-safe, but the resulting routers are.
     *
     * @param <S> Factory type for building routes.
     * @param <T> Target type for built routes.
     */
    public static class Builder<S extends Supplier<T>, T> {

        private S defaultTargetFactory;
        private final Map<PathTemplatePatternEqualsAdapter<Template<S>>, S> templates = new HashMap<>();

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
         * @param <A> New target factory type.
         * @param <B> New target type.
         * @param defaultTargetFactory The default target factory.
         *
         * @return Reference to the builder.
         */
        @SuppressWarnings("unchecked") //Always safe based on the encapsulated features of this builder.
        public <A extends Supplier<B>, B> Builder<A, B> updateDefaultTargetFactory(final A defaultTargetFactory) {
            if (!templates.isEmpty()) {
                throw new IllegalStateException("updateDefaultTargetFactory cannot be called after having added "
                        + "templates. Use defaultTarget instead");
            }

            Builder b = this;
            b.defaultTargetFactory = defaultTargetFactory;
            return b;
        }

        /**
         * Sets the default target to any type - provided that no templates have been added.
         *
         * @param <B> New target type.
         * @param defaultTarget The default target.
         *
         * @return Reference to the builder.
         */
        @SuppressWarnings("unchecked") //Always safe based on the encapsulated features of this builder.
        public <B> Builder<Supplier<B>, B> updateDefaultTarget(final B defaultTarget) {
            Objects.requireNonNull(defaultTarget);

            if (!templates.isEmpty()) {
                throw new IllegalStateException("updateDefaultTarget cannot be called after having added "
                        + "templates. Use defaultTarget instead");
            }

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
            final PathTemplatePatternEqualsAdapter<Template<S>> key = new PathTemplatePatternEqualsAdapter<>(template);
            return templates.get(key);
        }

        /**
         * @return A mutable map of {@link PathTemplatePatternEqualsAdapter}s for all templates added to this builder.
         */
        public Map<PathTemplatePatternEqualsAdapter<Template<S>>, S> getTemplates() {
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
            final PathTemplatePatternEqualsAdapter<Template<S>> item = new PathTemplatePatternEqualsAdapter<>(template);
            if (templates.containsKey(item)) {
                throw new IllegalArgumentException("The builder already contains a template with the same "
                        + "pattern for '" + template.pathTemplate + "'");
            }
            templates.put(item, template.target);
            return this;
        }

        /**
         * Adds the specified template to this builder, provided that it has not been added yet. Throws
         * {@link IllegalArgumentException} if this builder already contains the template.
         *
         * @param pathTemplate The path template.
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

            //Always safe based on how PathTemplatePatternEqualsAdapter uses the template in equals/hashCode
            @SuppressWarnings("unchecked")
            final PathTemplatePatternEqualsAdapter<Template<S>> item = new PathTemplatePatternEqualsAdapter<>(
                    (Template<S>) template
            );
            templates.remove(item);
            return this;
        }

        /**
         * @return A new router based on this builder.
         */
        public PathTemplateRouter<T> build() {
            if (defaultTargetFactory == null) {
                throw new IllegalStateException("The 'defaultTargetFactory' must be set before calling 'build'");
            }

            return new RouterFactory<>(
                    templates.keySet().stream()
                            .map(PathTemplatePatternEqualsAdapter::getPattern)
                            .collect(Collectors.toSet()),
                    defaultTargetFactory.get()
            ).create();
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="SimpleBuilder inner class">
    /**
     * A simplistic version of {@link Builder} with convenience methods for adding targets directly without using factories.
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
         * @param <T> Target type for built routes.
         * @param defaultTarget The default target. May be {@code NULL} in which case is should be specified with a separate
         * call to {@link #defaultTarget(java.lang.Object) }.
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
         * @return Reference to the builder.
         */
        public static SimpleBuilder<Void> newBuilder() {
            final Builder<Supplier<Void>, Void> builder = Builder.newBuilder();
            return new SimpleBuilder<>(builder);
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
         * @return A mutable map of {@link PathTemplatePatternEqualsAdapter}s for all templates added to this builder.
         */
        public Map<PathTemplatePatternEqualsAdapter<Template<Supplier<T>>>, Supplier<T>> getTemplates() {
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
         * @param pathTemplate The path template.
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
         * @param target The target.
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
        public PathTemplateRouter<T> build() {
            return builder.build();
        }
    }

    //</editor-fold>
    //
    private static final RouteRequest EMPTY_REQUEST = new RouteRequest(new char[0], 0, new int[0], 0);
    private static final RouteRequest CANT_MATCH_REQUEST = new RouteRequest(new char[0], 0, new int[0], 0);
    private static final boolean DEBUG = false;

    private PathTemplateRouterFactory() {
    }

    private static int getMinSegmentCount(final Matcher<?>[] matchers) {
        for (int i = 0; i < matchers.length; i++) {
            if (matchers[i] != null) {
                return i;
            }
        }
        throw new IllegalArgumentException("At least one segment matcher must be supplied");
    }

    private static int compareMostSpecificToLeastSpecific(
            final AbstractTemplateSegment o1,
            final AbstractTemplateSegment o2
    ) {
        if (o1.segmentIdx != o2.segmentIdx) {
            return o2.segmentIdx - o1.segmentIdx;
        }

        if (o1 instanceof StaticTemplateSegment) {
            final StaticTemplateSegment o1Static = (StaticTemplateSegment) o1;
            if (o2 instanceof StaticTemplateSegment) {
                final StaticTemplateSegment o2Static = (StaticTemplateSegment) o2;
                return o2Static.value.length() - o1Static.value.length();
            }
            return -1;
        }

        if (o1 instanceof ParamTemplateSegment) {
            if (o2 instanceof StaticTemplateSegment) {
                return 1;
            }

            if (o2 instanceof ParamTemplateSegment) {
                return 0;
            }

            if (o2 instanceof WildCardTemplateSegment) {
                return -1;
            }

            throw new IllegalArgumentException("Should never happend");
        }

        if (o1 instanceof WildCardTemplateSegment) {
            final WildCardTemplateSegment o1WildCard = (WildCardTemplateSegment) o1;
            if (o2 instanceof StaticTemplateSegment) {
                return 1;
            }

            if (o2 instanceof ParamTemplateSegment) {
                return 1;
            }

            if (o2 instanceof WildCardTemplateSegment) {
                final WildCardTemplateSegment o2WildCard = (WildCardTemplateSegment) o2;
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
        for (final Matcher<? extends T> matcher : matchers) {
            result[idx++] = matcher;
        }
        return result;
    }

    /**
     * Creates a map of parameters - that are extracted from the specified request. The resulting map is mutable.
     *
     * @param paramSegmentIndexes Indexes of the segments that are parameters.
     * @param paramNames Names for the parameters.
     * @param paramCount Total count of parameters. Must be equal to paramSegmentIndexes.length and paramNames.length. Receiving
     * this - instead of calculating is an optimisation that flows from the Matcher strategies that use this method. Care must
     * be taken to ensure that it is accurate as it isn't validated during routing.
     * @param paramMapSize Initial size of the resulting map. Provided as an optimisation in case the map needs to cater for
     * further values being added without having to resize/rebalance.
     * @param request The request.
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
     * Creates a map of parameters that contains exactly one entry with the specified name and value. This is essentially an
     * alternative to {@link Map#of(java.lang.Object, java.lang.Object) } that returns a mutable map.
     *
     * @param name Name for the parameter.
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
        if (len == 0 || path[0] != '/') {
            throw new IllegalArgumentException();
        }
        if (len == 1) {
            return 0;
        }
        if (path[len - 1] == '/') {
            --len;
        }

        int result = 1;
        char lastChar = '/';
        for (int i = 1; i < len; i++) {
            if (path[i] == '/') {
                if (lastChar == '/') {
                    throw new IllegalArgumentException();
                }
                ++result;
            }
            lastChar = path[i];
        }

        return result;
    }

    /**
     * Finds the first index in the array that contains the specified key.
     *
     * @param a The array to be searched.
     * @param fromIndex The index of the first element (inclusive) to be searched
     * @param toIndex The index of the last element (exclusive) to be searched.
     * @param key The value to be searched for.
     *
     * @return Index of the first element in the array - between fromIndex and toIndex - that is equal to the specified keys.
     * Returns -1 if the specified key is not found between fromIndex and toIndex.
     *
     * @throws IllegalArgumentException If {@code fromIndex > toIndex}.
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
            if (a[i] == key) {
                return i;
            }
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

            if (!valid) {
                throw new IllegalArgumentException("Illegal character '"
                        + c + "' is contained in the segment '" + value + "' at position " + i);
            }
        }

        return value;
    }

    private static AbstractTemplateSegment createSegment(
            final int segmentIdx,
            final char[] template,
            final int startIdx,
            final int endIdx
    ) {
        // This is an empty static segment.  Mostly used by templates ending with '/' as the final segment.
        if (startIdx == endIdx) {
            return new StaticTemplateSegment(segmentIdx, "");
        }

        // Starts and ends with '{' and '}', implies that it is a parameter segment.
        if (template[startIdx] == '{' && template[endIdx - 1] == '}') {
            return new ParamTemplateSegment(segmentIdx, validSegment(template, startIdx + 1, endIdx - 2));
        }

        // Contains an '*', implies that it is a wildcard segment.
        int idx = firstIndexOf(template, startIdx, endIdx, '*');
        if (idx != -1) {
            final String prefix = validSegment(template, startIdx, idx - 1);
            return new WildCardTemplateSegment(segmentIdx, prefix);
        }

        // Defaults to a static segment.
        return new StaticTemplateSegment(segmentIdx, validSegment(template, startIdx, endIdx - 1));
    }

    private static char[] pathWithForwardSlash(
            final String pathString,
            final int pathLength
    ) {
        if (pathLength == 0) {
            return new char[]{'/'};
        }

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
        if (pathLength == -1) {
            pathLength = pathString.length();
        }
        final char[] path = pathWithForwardSlash(pathString, pathLength);

        /*  We know the path starts with '/', so if the length is 1 then it is a request for root. If that is a routable
            path - based on minSegmentCount - then we return an empty request, otherwise we know it is not routable. */
        int pathLen = path.length;
        if (pathLen == 1) {
            return minSegmentCount > 0 ? CANT_MATCH_REQUEST : EMPTY_REQUEST;
        }

        /*  Quick count of the segments to create an array of the correct size. We also exit immediately if it becomes
            clear that we have less or more segments than the router has templates for */
        int cnt = 1;
        char c = '/';
        for (int i = 1; i < pathLen; i++) {
            if (path[i] == '/') {
                if (c == '/') {
                    return CANT_MATCH_REQUEST;
                }
                if (++cnt > maxSegmentCount) {
                    return CANT_MATCH_REQUEST;
                }
            }
            c = path[i];
        }
        if (cnt < minSegmentCount) {
            return CANT_MATCH_REQUEST;
        }

        //Let's create the segments.
        //The start indexes has a special last entry, which is the pathLen + 1 to simplify code in matchers.
        final int[] segmentStartIndexes = new int[cnt + 1];
        segmentStartIndexes[0] = 1;
        cnt = 1;
        for (int i = 1; i < pathLen; i++) {
            if (path[i] == '/') {
                segmentStartIndexes[cnt++] = i + 1;
            }
        }
        segmentStartIndexes[cnt] = pathLen + 1;

        return new RouteRequest(path, pathLen, segmentStartIndexes, cnt);
    }

    private static void validWildCardOrNotAWildCard(final String pathTemplate) {
        final int idx = pathTemplate.indexOf('*');
        if (idx == -1) {
            return;
        }

        if (idx != (pathTemplate.length() - 1)) {
            throw new IllegalArgumentException("Wild cards are only supported at the end of a template path");
        }
    }

    /**
     * Parses the URL path - with optional path parameters - into a template.
     *
     * @param <T> Target type.
     * @param pathTemplate The path template.
     * @param target The target.
     *
     * @return The template.
     */
    public static <T> Template<T> parseTemplate(final String pathTemplate, final T target) {
        if (pathTemplate == null || target == null) {
            throw new IllegalArgumentException();
        }
        validWildCardOrNotAWildCard(pathTemplate);

        // Work directly with char array for performance reasons.  We  ensure that the path starts with a '/'.
        final char[] template = pathWithForwardSlash(pathTemplate, pathTemplate.length());

        final int segmentCount = countSegments(template);

        int len = template.length;

        final List<AbstractTemplateSegment> segments = new ArrayList<>(segmentCount);
        int start = 1, segmentIdx = 0;
        AbstractTemplateSegment segment;
        boolean wildCard = false;
        for (int i = 2; i <= len; i++) {
            if (i == len || template[i] == '/') {
                segment = createSegment(segmentIdx++, template, start, i);
                wildCard = wildCard || (segment instanceof WildCardTemplateSegment);
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
     * @param <T> Target type.
     * @param templates The templates.
     * @param defaultTarget The default target.
     *
     * @return The router.
     */
    public static <T> PathTemplateRouter<T> createRouter(
            final Set<? extends Template<? extends Supplier<? extends T>>> templates,
            final T defaultTarget
    ) {
        return new RouterFactory<>(templates, defaultTarget).create();
    }
}
