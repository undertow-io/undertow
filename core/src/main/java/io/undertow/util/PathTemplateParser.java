/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import io.undertow.UndertowMessages;
import static io.undertow.util.PathTemplateUtil.pathWithForwardSlash;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods and classes for parsing formatted strings into URL path templates.
 * <p>
 * This is one of three main classes that participate in setting up and routing requested URL paths to URL path templates:
 * <ol>
 * <li>{@link PathTemplateParser} participates in the <i>*setup phase</i> by parsing formatted strings into URL path
 * templates.</li>
 * <li>{@link PathTemplateRouterFactory} participates in the <i>*setup phase</i> by creating router instances from sets of
 * parsed URL path templates.</li>
 * <li>{@link PathTemplateRouter} participates in the <i>*routing phase</i> by routing requested URL paths to sets of underlying
 * URL path templates.</li>
 * </ol>
 * <p>
 * <i>* The setup and routing phases are documented in {@link PathTemplateRouter}.</i>
 *
 * <p>
 * <b>URL path template strings</b>
 *
 * <p>
 * URL path templates are parsed from formatted strings that must adhere to the following specification:
 *
 * <ol>
 * <li>Start with a {@code /} (forward slash) character, i.e.: {@code /books}.</li>
 * <li>Contain zero or more segments, delimited by {@code /} (forward slash) characters. The following three segment types are
 * supported:
 * <ol>
 * <li>Static strings. The string {@code "/books/{bookId}/chapters"} is a valid template string that contains the static string
 * {@code "books"} as its first segment and the static string {@code "chapters"} as its third segment.</li>
 * <li>Named parameters. The string {@code "/books/{bookId}/chapters"} is a valid template string that contains the named
 * parameter {@code "bookId"} as its second segment.</li>
 * <li>Wildcards. The string {@code "/books/{bookId}/*"} is a valid template string that contains a wildcard as its third
 * segment. Wildcards may be prefixed by static strings, i.e. the string {@code "/books/{bookId}/chapters*"} is also a valid
 * template string.</li>
 * </ol>
 * </li>
 * <li>Contain at most one wildcard. If a wildcard is present, it must be the last character in the template string.</li>
 * </ol>
 * <p>
 * <i>The routing methodology for segment types and combinations of segment types are documented in
 * {@link PathTemplateRouter}.</i>
 *
 * @author Dirk Roets
 */
public class PathTemplateParser {

    //<editor-fold defaultstate="collapsed" desc="Design and implementation notes">
    /*
    Design Decisions

    (Also read the design decisions section in PathTemplateRouterFactory)

    Implementation Notes

    This parser encapsulates the following classes:

        1.  PathTemplate (Setup phase). URL path template strings are structurally validated and then parsed into instances of
            PathTemplate. PathTemplate strings are eagerly parsed into Templates and eagerly checked for
            uniqueness of their patterns by the builders in PathTemplateRouterFactory. The objective there is to fail fast -
            with an exception - as soon as a duplicate pattern is added. This approach will make it easier for developers to
            know which pattern is duplicated and where it is duplicated in their code. Templates consist of 0 or more segments
            with each segment having a fixed position inside of the template. 3 different types of segments are supported by
            templates, each represented by its own class. These instances are recognised by the PathTemplateRouterFactory that
            then generates matchers that will be used to recognise matches during the routing phase.

        2.  PathTemplatePatternEqualsAdapter (Setup phase). Instances of PathTemplate are wrapped inside of
            PathTemplatePatternEqualsAdapter which allows duplicate template patterns to be detected. For example,
            '/books/{varNameA}/chapters' and '/books/{varNameB}/chapters' are different templates, but represent the same
            pattern of: ['books'],[named variable],['chapters']. It does not make sense to allow multiple templates that have
            the same pattern to be added to the same router as all requested paths that match the first template will also
            match the second template and there is no predictable way to determine which is the better match.

        3.  PathTemplate segments (Multiple classes. Setup phase). Represents the three different types of segments that appear
            at specific positions inside of templates.
     */
    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="PathTemplatePattern inner class">
    /**
     * Parent class for components of URL path templates that can be compared as "patterns".
     *
     * <p>
     * This class is nested inside the parser and not meant to be extended by classes outside of the parser. The current
     * implementations of the parser and router factory do not support custom extensions, therefore this should be viewed as
     * hermetic.</p>
     *
     * <p>
     * This class is extended by all three types of path template segments (one type of component) as well as by the path
     * template (container) itself (The second type of component).</p>
     *
     * The objective of this class is to provide a contract that - when extended - can be wrapped inside of
     * {@link PathTemplatePatternEqualsAdapter} in order to determine if two or more components represent the same pattern. Two
     * patterns are considered to be the same (equal) pattern when ALL possible paths that match the one pattern also matches
     * the second pattern. For example, the following two path templates are not equal due to different variable names being
     * used, but the patterns represented by the two paths are equal as all paths that match the first template will also match
     * the second template:
     * <ol>
     * <li>/books/{bookId}/chapters</li>
     * <li>/books/{bookName}/chapters</li>
     * </ol>
     *
     * <p>
     * In the above mentioned example the {@link #hashCode() } method may return different values for the two templates and
     * {@link #equals(java.lang.Object) } will return 'false'. The {@link #patternHashCode() } will return the same values for
     * the two templates and {@link #patternEquals(io.undertow.util.PathTemplateParser.PathTemplatePattern) } will return 'true'.</p>
     */
    public abstract static class PathTemplatePattern {

        private PathTemplatePattern() {
        }

        /**
         * Returns a hash code value for the pattern represented by this component.
         *
         * @return The hash code.
         */
        protected abstract int patternHashCode();

        /**
         * True if the pattern represented by this component is equal to the pattern represented by the specified component.
         *
         * @param pattern The pattern.
         *
         * @return True if this pattern represents the same 'pattern' as the specified pattern.
         */
        protected abstract boolean patternEquals(PathTemplatePattern pattern);
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="PathTemplatePatternEqualsAdapter inner class">
    /**
     * An object adapter that wraps {@link PathTemplatePattern} and that uses its patternHashCode and patternEquals methods as
     * the standard hashCode and equals methods.
     *
     * <p>
     * The objective of this class is to enable the use of "patterns" as keys for maps / sets etc.</p>
     *
     * @param <T> Type of path template patterns.
     */
    public static class PathTemplatePatternEqualsAdapter<T extends PathTemplatePattern> {

        private final T pattern;

        /**
         * @param pattern The pattern.
         */
        public PathTemplatePatternEqualsAdapter(final T pattern) {
            this.pattern = Objects.requireNonNull(pattern);
        }

        @Override
        public String toString() {
            return "PathTemplatePatternEqualsAdapter{" + "element=" + pattern + '}';
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

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Path template segment inner classes">
    /**
     * Parent class for all segments inside a parsed URL path template ({@link PathTemplate}).
     *
     * For the purposes of this class a segment is defined as the parts of a URL path template that are separated by '/'
     * characters.
     *
     * Extensions of this class must be immutable.
     */
    public abstract static class AbstractTemplateSegment extends PathTemplatePattern {

        private final int segmentIdx;

        private AbstractTemplateSegment(final int segmentIdx) {
            this.segmentIdx = segmentIdx;

            if (segmentIdx < 0) { //0-based index for an array
                throw new IllegalArgumentException();
            }
        }

        /**
         * @return Index for this segment in the array of segments for the template.
         */
        public int getSegmentIdx() {
            return segmentIdx;
        }
    }

    /**
     * A static string that must be exactly equal to the content in the same segment of a requested path in order for the
     * segment to be considered a 'match' for the request.
     *
     * Instances of this class are immutable.
     */
    public static class StaticTemplateSegment extends AbstractTemplateSegment {

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
            return "StaticTemplateSegment{" + "segmentIdx=" + getSegmentIdx() + ", value=" + value + '}';
        }

        /**
         * @return The static string that must be exactly equal to the content in the same segment of a requested path.
         */
        public String getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 67 * hash + this.getSegmentIdx();
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
            if (this.getSegmentIdx() != other.getSegmentIdx()) {
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
     * A named parameter that will match any content in the same segment of a requested path.
     *
     * Instances of this class are immutable.
     */
    public static class ParamTemplateSegment extends AbstractTemplateSegment {

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
            return "ParamTemplateSegment{" + "segmentIdx=" + getSegmentIdx() + ", paramName=" + paramName + '}';
        }

        /**
         *
         * @return Name for the parameter, excluding the '{' and '}' braces.
         */
        public String getParamName() {
            return paramName;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + this.getSegmentIdx();
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
            if (this.getSegmentIdx() != other.getSegmentIdx()) {
                return false;
            }
            return Objects.equals(this.paramName, other.paramName);
        }

        @Override
        public int patternHashCode() {
            int hash = 5;
            hash = 53 * hash + this.getSegmentIdx();
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
            return this.getSegmentIdx() == other.getSegmentIdx();
        }
    }

    /**
     * A wildcard that will match any content (for any number of segments) that follows the '*' wildcard character.
     *
     * Instances of this class are immutable.
     */
    public static class WildCardTemplateSegment extends AbstractTemplateSegment {

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
            return "WildCardTemplateSegment{" + "segmentIdx=" + getSegmentIdx() + ", prefix=" + prefix + '}';
        }

        /**
         * @return Prefix for the wild card, excluding the '*' itself. May be an empty string.
         */
        public String getPrefix() {
            return prefix;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + this.getSegmentIdx();
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
            if (this.getSegmentIdx() != other.getSegmentIdx()) {
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
    //<editor-fold defaultstate="collapsed" desc="PathTemplate inner class">
    /**
     * A parsed URL that consists of a list of segments. A URL path template is a pattern against which requested URL paths can
     * be matched. Path templates are parsed from formatted strings, see
     * {@link #parseTemplate(java.lang.String, java.lang.Object) }.
     *
     * Parsers and routers support 3 different types of segments in templates:
     * <ol>
     * <li>Static segments. These strings must be exactly equal to the segments in the same positions of requested paths to be
     * considered a match for the segment.</li>
     * <li>Named parameters. These segments will match any content for segments in the same positions of requested paths. The
     * content will be returned as the values for the parameters with the associated names in routing results.</li>
     * <li>Wildcards. These segments will match any content for the same segments as well as any content from any segments that
     * appear after the position of the wildcards in requested paths.</li>
     * </ol>
     *
     * The following specification applies to URL path templates in string format:
     * <ol>
     * <li>Templates start with the '/' character.</li>
     * <li>Segments in templates are delimited using the '/' character.</li>
     * <li>Named parameters are enclosed inside '{' and '}'.</li>
     * <li>Named parameters always represent an entire segment. Mixing static strings or wildcards with named parameters within
     * one segment is not supported.</li>
     * <li>Wildcards are denoted by the '*' character.</li>
     * <li>Templates may only contain one wildcard segment and - when present - the wildcard '*' character must always be the
     * last character in the template.</li>
     * <li>Wildcards may be prefixed by a static string within the same segment.</li>
     * </ol>
     *
     * Instances of this class are thread-safe.
     *
     * @param <T> Target type.
     */
    public static class PathTemplate<T> extends PathTemplatePattern {

        private final String pathTemplate;
        private final List<AbstractTemplateSegment> segments;
        private final T target;
        private final boolean wildCard;

        private PathTemplate(
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
            final PathTemplate<?> other = (PathTemplate<?>) obj;
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

            final PathTemplate<?> other = (PathTemplate<?>) obj;
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
         * @return The URL path template string from which this template was parsed.
         */
        public String getPathTemplate() {
            return pathTemplate;
        }

        /**
         * @return List of segments that make up this template.
         */
        public List<AbstractTemplateSegment> getSegments() {
            return segments;
        }

        /**
         * @return The target for this template.
         */
        public T getTarget() {
            return target;
        }

        /**
         * @return True if this template contains a wild card.
         */
        public boolean getWildCard() {
            return wildCard;
        }
    }

    //</editor-fold>
    //
    private PathTemplateParser() {
    }

    private static void validWildCardOrNotAWildCard(final String pathTemplate) {
        final int idx = pathTemplate.indexOf('*');
        if (idx == -1) {
            return;
        }

        if (idx != (pathTemplate.length() - 1)) {
            throw UndertowMessages.MESSAGES.wildCardsOnlyAtEndOfTemplate();
        }
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
                throw UndertowMessages.MESSAGES.illegalCharacterInPathSegment(c, value, i);
            }
        }

        return value;
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

    /**
     * Parses a formatted string into a URL path template.
     *
     * <p>
     * Formatted strings that must adhere to the following specification:</p>
     *
     * <ol>
     * <li>Start with a {@code /} (forward slash) character, i.e.: {@code /books}.</li>
     * <li>Contain zero or more segments, separated by {@code /} (forward slash) characters. The following three segment types
     * are supported:
     * <ol>
     * <li>Static strings. The string {@code "/books/{bookId}/chapters"} is a valid template string that contains the static
     * string {@code "books"} as its first segment and the static string {@code "chapters"} as its third segment. The content in
     * the same segment of a requested path must match the static string exactly in order to be considered a match.</li>
     * <li>Named parameters. The string {@code "/books/{bookId}/chapters"} is a valid template string that contains the named
     * parameter {@code "bookId"} as its second segment. Any content in the same segment of a requested path will match a named
     * parameter.</li>
     * <li>Wildcards. The string {@code "/books/{bookId}/*"} is a valid template string that contains a wildcard as its third
     * segment. Wildcards may be prefixed by static strings, i.e. the string {@code "/books/{bookId}/chapters*"} is also a valid
     * template string. Any content (in any number of segments) that follows the wildcard character in a requested path, will be
     * a match for the wildcard.</li>
     * </ol>
     * </li>
     * <li>Named parameters are enclosed inside '{' and '}' characters.</li>
     * <li>Named parameters always represent an entire segment. Mixing static strings or wildcards with named parameters within
     * one segment is not supported.</li>
     * <li>Wildcards are denoted by the {@code '*'} character.</li>
     * <li>Contain at most one wildcard. If a wildcard is present, it must be the last character in the template string.</li>
     * </ol>
     *
     * @param <T> Target type.
     * @param pathTemplate The path template.
     * @param target The target.
     *
     * @return The template.
     */
    public static <T> PathTemplate<T> parseTemplate(final String pathTemplate, final T target) {
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
        return new PathTemplate<>(
                pathTemplate,
                List.copyOf(segments),
                wildCard,
                target
        );
    }
}
