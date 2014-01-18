package io.undertow.util;

import io.undertow.UndertowMessages;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility class that provides fast path matching of path templates. Templates are stored in a map based on the stem of the template,
 * and matches longest stem first.
 * <p/>
 * TODO: we can probably do this faster using a trie type structure, but I think the current impl should perform ok most of the time
 *
 * @author Stuart Douglas
 */
public class PathTemplateMatcher<T> {

    /**
     * Map of path template stem to the path templates that share the same base.
     */
    private Map<String, Set<PathTemplateHolder>> pathTemplateMap = new CopyOnWriteMap<String, Set<PathTemplateHolder>>();

    /**
     * lengths of all registered paths
     */
    private volatile int[] lengths = {};

    public PathMatchResult<T> match(final String path) {
        final Map<String, String> params = new HashMap<String, String>();
        int length = path.length();
        final int[] lengths = this.lengths;
        for (int i = 0; i < lengths.length; ++i) {
            int pathLength = lengths[i];
            if (pathLength == length) {
                Set<PathTemplateHolder> entry = pathTemplateMap.get(path);
                if (entry != null) {
                    PathMatchResult<T> res = handleStemMatch(entry, path, params);
                    if (res != null) {
                        return res;
                    }
                }
            } else if (pathLength < length) {
                char c = path.charAt(pathLength);
                if (c == '/') {
                    String part = path.substring(0, pathLength);
                    Set<PathTemplateHolder> entry = pathTemplateMap.get(part);
                    if (entry != null) {
                        PathMatchResult<T> res = handleStemMatch(entry, path, params);
                        if (res != null) {
                            return res;
                        }
                    }
                }
            }
        }
        return null;
    }

    private PathMatchResult<T> handleStemMatch(Set<PathTemplateHolder> entry, final String path, final Map<String, String> params) {
        for (PathTemplateHolder val : entry) {
            if (val.template.matches(path, params)) {
                return new PathMatchResult<T>(params, val.template.getTemplateString(), val.value);
            } else {
                params.clear();
            }
        }
        return null;
    }


    public synchronized PathTemplateMatcher<T> add(final PathTemplate template, final T value) {
        Set<PathTemplateHolder> values = pathTemplateMap.get(trimBase(template));
        Set<PathTemplateHolder> newValues;
        if (values == null) {
            newValues = new TreeSet<PathTemplateHolder>();
        } else {
            newValues = new TreeSet<PathTemplateHolder>(values);
        }
        PathTemplateHolder holder = new PathTemplateHolder(value, template);
        if (newValues.contains(holder)) {
            PathTemplate equivalent = null;
            for (PathTemplateHolder item : newValues) {
                if (item.compareTo(holder) == 0) {
                    equivalent = item.template;
                    break;
                }
            }
            throw UndertowMessages.MESSAGES.matcherAlreadyContainsTemplate(template.getTemplateString(), equivalent.getTemplateString());
        }
        newValues.add(holder);
        pathTemplateMap.put(trimBase(template), newValues);
        buildLengths();
        return this;
    }

    private String trimBase(PathTemplate template) {
        if (template.getBase().endsWith("/")) {
            return template.getBase().substring(0, template.getBase().length() - 1);
        }
        return template.getBase();
    }

    private void buildLengths() {
        final Set<Integer> lengths = new TreeSet<Integer>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -o1.compareTo(o2);
            }
        });
        for (String p : pathTemplateMap.keySet()) {
            lengths.add(p.length());
        }

        int[] lengthArray = new int[lengths.size()];
        int pos = 0;
        for (int i : lengths) {
            lengthArray[pos++] = i; //-1 because the base paths end with a /
        }
        this.lengths = lengthArray;
    }

    public synchronized PathTemplateMatcher<T> add(final String pathTemplate, final T value) {
        final PathTemplate template = PathTemplate.create(pathTemplate);
        return add(template, value);
    }

    public synchronized PathTemplateMatcher<T> remove(final String pathTemplate) {
        final PathTemplate template = PathTemplate.create(pathTemplate);
        return remove(template);
    }

    private synchronized PathTemplateMatcher<T> remove(PathTemplate template) {
        Set<PathTemplateHolder> values = pathTemplateMap.get(trimBase(template));
        Set<PathTemplateHolder> newValues;
        if (values == null) {
            newValues = new TreeSet<PathTemplateHolder>();
        } else {
            newValues = new TreeSet<PathTemplateHolder>(values);
        }
        Iterator<PathTemplateHolder> it = newValues.iterator();
        while (it.hasNext()) {
            PathTemplateHolder next = it.next();
            if (next.template.getTemplateString().equals(template.getTemplateString())) {
                it.remove();
                break;
            }
        }
        if (newValues.size() == 0) {
            pathTemplateMap.remove(trimBase(template));
        } else {
            pathTemplateMap.put(trimBase(template), newValues);
        }
        buildLengths();
        return this;
    }

    public static class PathMatchResult<T> {
        private final Map<String, String> parameters;
        private final String matchedTemplate;
        private final T value;

        public PathMatchResult(Map<String, String> parameters, String matchedTemplate, T value) {
            this.parameters = parameters;
            this.matchedTemplate = matchedTemplate;
            this.value = value;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public String getMatchedTemplate() {
            return matchedTemplate;
        }

        public T getValue() {
            return value;
        }
    }

    private final class PathTemplateHolder implements Comparable<PathTemplateHolder> {
        final T value;
        final PathTemplate template;

        private PathTemplateHolder(T value, PathTemplate template) {
            this.value = value;
            this.template = template;
        }

        @Override
        public int compareTo(PathTemplateHolder o) {
            return template.compareTo(o.template);
        }
    }

}
