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

import io.undertow.UndertowMessages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Represents a parsed web socket path template.
 * <p>
 * This class can be compared to other path templates, with templates that are considered
 * lower have a higher priority, and should be checked first.
 * <p>
 * This comparison can also be used to check for semantically equal paths, if
 * a.compareTo(b) == 0 then the two paths are equivalent, which will generally
 * result in a deployment exception.
 *
 * @author Stuart Douglas
 */
public class PathTemplate implements Comparable<PathTemplate> {

    private final String templateString;
    private final boolean template;
    private final String base;
    private final List<Part> parts;
    private final Set<String> parameterNames;

    private PathTemplate(String templateString, final boolean template, final String base, final List<Part> parts, Set<String> parameterNames) {
        this.templateString = templateString;
        this.template = template;
        this.base = base;
        this.parts = parts;
        this.parameterNames = Collections.unmodifiableSet(parameterNames);
    }

    public static PathTemplate create(final String inputPath) {
        // a path is required
        if(inputPath == null) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }

        // prepend a "/" if none is present
        if(!inputPath.startsWith("/")) {
            return PathTemplate.create("/" + inputPath);
        }

        // create string from modified string
        final String path = inputPath;

        int state = 0;
        String base = "";
        List<Part> parts = new ArrayList<>();
        int stringStart = 0;
        //0 parsing base
        //1 parsing base, last char was /
        //2 in template part
        //3 just after template part, expecting /
        //4 expecting either template or segment
        //5 in segment

        for (int i = 0; i < path.length(); ++i) {
            final int c = path.charAt(i);
            switch (state) {
                case 0: {
                    if (c == '/') {
                        state = 1;
                    } else {
                        state = 0;
                    }
                    break;
                }
                case 1: {
                    if (c == '{') {
                        base = path.substring(0, i);
                        stringStart = i + 1;
                        state = 2;
                    } else if (c != '/') {
                        state = 0;
                    }
                    break;
                }
                case 2: {
                    if (c == '}') {
                        Part part = new Part(true, path.substring(stringStart, i));
                        parts.add(part);
                        stringStart = i;
                        state = 3;
                    }
                    break;
                }
                case 3: {
                    if (c == '/') {
                        state = 4;
                    } else {
                        throw UndertowMessages.MESSAGES.couldNotParseUriTemplate(path, i);
                    }
                    break;
                }
                case 4: {
                    if (c == '{') {
                        stringStart = i + 1;
                        state = 2;
                    } else if (c != '/') {
                        stringStart = i;
                        state = 5;
                    }
                    break;
                }
                case 5: {
                    if (c == '/') {
                        Part part = new Part(false, path.substring(stringStart, i));
                        parts.add(part);
                        stringStart = i + 1;
                        state = 4;
                    }
                    break;
                }
            }
        }

        switch (state) {
            case 0:
            case 1: {
                base = path;
                break;
            }
            case 2: {
                throw UndertowMessages.MESSAGES.couldNotParseUriTemplate(path, path.length());
            }
            case 5: {
                Part part = new Part(false, path.substring(stringStart));
                parts.add(part);
                break;
            }
        }
        final Set<String> templates = new HashSet<>();
        for(Part part : parts) {
            if(part.template) {
                templates.add(part.part);
            }
        }
        return new PathTemplate(path, state > 1, base, parts, templates);
    }

    /**
     * Check if the given uri matches the template. If so then it will return true and
     * place the value of any path parameters into the given map.
     * <p>
     * Note the map may be modified even if the match in unsuccessful, however in this case
     * it will be emptied before the method returns
     *
     * @param path           The request path, relative to the context root
     * @param pathParameters The path parameters map to fill out
     * @return true if the URI is a match
     */
    public boolean matches(final String path, final Map<String, String> pathParameters) {

        if (!template && base.contains("*")) {
            final int indexOf = base.indexOf("*");
            final String startBase = base.substring(0, indexOf);
            if (!path.startsWith(startBase)) {
                return false;
            }
            pathParameters.put("*", path.substring(indexOf,path.length()));
            return true;
        }


        if (!path.startsWith(base)) {
            return false;
        }
        int baseLength = base.length();
        if (!template) {
            return path.length() == baseLength;
        }

        int currentPartPosition = 0;
        PathTemplate.Part current = parts.get(currentPartPosition);
        int stringStart = baseLength;
        int i;
        for (i = baseLength; i < path.length(); ++i) {
            final char currentChar = path.charAt(i);
            if (currentChar == '?' || current.part.equals("*")) {
                break;
            } else if (currentChar == '/') {
                String result = path.substring(stringStart, i);
                if (current.template) {
                    pathParameters.put(current.part, result);
                } else if (!result.equals(current.part)) {
                    pathParameters.clear();
                    return false;
                }
                ++currentPartPosition;
                if (currentPartPosition == parts.size()) {
                    //this is a match if this is the last character
                    return i == (path.length() - 1);
                }
                current = parts.get(currentPartPosition);
                stringStart = i + 1;
            }
        }
        if (currentPartPosition + 1 != parts.size()) {
            pathParameters.clear();
            return false;
        }

        String result = path.substring(stringStart, i);
        if (current.part.equals("*")) {
            pathParameters.put(current.part, path.substring(stringStart,path.length()));
            return true;
        }
        if (current.template) {
            pathParameters.put(current.part, result);
        } else if (!result.equals(current.part)) {
            pathParameters.clear();
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathTemplate)) return false;

        PathTemplate that = (PathTemplate) o;

        return this.compareTo(that) == 0;

    }

    @Override
    public int hashCode() {
        int result = getTemplateString() != null ? getTemplateString().hashCode() : 0;
        result = 31 * result + (template ? 1 : 0);
        result = 31 * result + (getBase() != null ? getBase().hashCode() : 0);
        result = 31 * result + (parts != null ? parts.hashCode() : 0);
        result = 31 * result + (getParameterNames() != null ? getParameterNames().hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(final PathTemplate o) {
        //we want templates with the highest priority to sort first
        //so we sort in reverse priority order

        //templates have lower priority
        if (template && !o.template) {
            return 1;
        } else if (o.template && !template) {
            return -1;
        }

        int res = base.compareTo(o.base);
        if (res > 0) {
            //our base is longer
            return -1;
        } else if (res < 0) {
            return 1;
        } else if (!template) {
            //they are the same path
            return 0;
        }

        //the first path with a non-template element
        int i = 0;
        for (; ; ) {
            if (parts.size() == i) {
                if (o.parts.size() == i) {
                    return base.compareTo(o.base);
                }
                return 1;
            } else if (o.parts.size() == i) {
                //we have more parts, so should be checked first
                return -1;
            }
            Part thisPath = parts.get(i);
            Part otherPart = o.parts.get(i);
            if (thisPath.template && !otherPart.template) {
                //non template part sorts first
                return 1;
            } else if (!thisPath.template && otherPart.template) {
                return -1;
            } else if (!thisPath.template) {
                int r = thisPath.part.compareTo(otherPart.part);
                if (r != 0) {
                    return r;
                }
            }
            ++i;
        }
    }

    public String getBase() {
        return base;
    }

    public String getTemplateString() {
        return templateString;
    }

    public Set<String> getParameterNames() {
        return parameterNames;
    }

    private static class Part {
        final boolean template;
        final String part;

        private Part(final boolean template, final String part) {
            this.template = template;
            this.part = part;
        }

        @Override
        public String toString() {
            return "Part{" +
                    "template=" + template +
                    ", part='" + part + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "PathTemplate{" +
                "template=" + template +
                ", base='" + base + '\'' +
                ", parts=" + parts +
                '}';
    }
}
