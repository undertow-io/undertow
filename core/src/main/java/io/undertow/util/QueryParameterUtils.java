package io.undertow.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Methods for dealing with the query string
 *
 * @author Stuart Douglas
 */
public class QueryParameterUtils {

    private QueryParameterUtils() {

    }

    public static String buildQueryString(final Map<String, Deque<String>> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Deque<String>> entry : params.entrySet()) {
            if (entry.getValue().isEmpty()) {
                if (first) {
                    first = false;
                } else {
                    sb.append('&');
                }
                sb.append(entry.getKey());
                sb.append('=');
            } else {
                for (String val : entry.getValue()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append('&');
                    }
                    sb.append(entry.getKey());
                    sb.append('=');
                    sb.append(val);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a query string into a map
     * @param newQueryString The query string
     * @return The map of key value parameters
     */
    public static Map<String, Deque<String>> parseQueryString(final String newQueryString) {
        Map<String, Deque<String>> newQueryParameters = new LinkedHashMap<String, Deque<String>>();
        int startPos = 0;
        int equalPos = -1;
        for(int i = 0; i < newQueryString.length(); ++i) {
            char c = newQueryString.charAt(i);
            if(c == '=' && equalPos == -1) {
                equalPos = i;
            } else if(c == '&') {
                handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, i);
                startPos = i + 1;
                equalPos = -1;
            }
        }
        if(startPos != newQueryString.length()) {
            handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, newQueryString.length());
        }
        return newQueryParameters;
    }

    private static void handleQueryParameter(String newQueryString, Map<String, Deque<String>> newQueryParameters, int startPos, int equalPos, int i) {
        String key;
        String value = "";
        if(equalPos == -1) {
            key = newQueryString.substring(startPos, i);
        } else {
            key = newQueryString.substring(startPos, equalPos);
            value = newQueryString.substring(equalPos + 1, i);
        }

        Deque<String> queue = newQueryParameters.get(key);
        if (queue == null) {
            newQueryParameters.put(key, queue = new ArrayDeque<String>(1));
        }
        if(value != null) {
            queue.add(value);
        }
    }


    public static Map<String, Deque<String>> mergeQueryParametersWithNewQueryString(final Map<String, Deque<String>> queryParameters, final String newQueryString) {

        Map<String, Deque<String>> newQueryParameters = parseQueryString(newQueryString);
        //according to the spec the new query parameters have to 'take precedence'
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
