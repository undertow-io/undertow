package io.undertow.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class QueryParameterUtils {

    public static Map<String, Deque<String>> parseQueryString(final String newQueryString) {
        Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
        for (String part : newQueryString.split("&")) {
            String name = part;
            String value = "";
            int equals = part.indexOf('=');
            if (equals != -1) {
                name = part.substring(0, equals);
                value = part.substring(equals + 1);
            }
            Deque<String> queue = newQueryParameters.get(name);
            if (queue == null) {
                newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
            }
            queue.add(value);
        }
        return newQueryParameters;
    }


    public static Map<String, Deque<String>> mergeQueryParametersWithNewQueryString(final Map<String, Deque<String>> queryParameters, final String newQueryString) {

        Map<String, Deque<String>> newQueryParameters = parseQueryString(newQueryString);
        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            if (!newQueryParameters.containsKey(entry.getKey())) {
                newQueryParameters.put(entry.getKey(), new ArrayDeque<String>(entry.getValue()));
            } else {
                newQueryParameters.get(entry.getKey()).addAll(entry.getValue());
            }
        }
        return newQueryParameters;
    }
}
