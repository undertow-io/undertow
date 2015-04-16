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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.xnio.OptionMap;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;

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
    @Deprecated
    public static Map<String, Deque<String>> parseQueryString(final String newQueryString) {
        return parseQueryString(newQueryString, null);
    }

    /**
     * Parses a query string into a map
     * @param newQueryString The query string
     * @return The map of key value parameters
     */
    public static Map<String, Deque<String>> parseQueryString(final String newQueryString, final String encoding) {
        Map<String, Deque<String>> newQueryParameters = new LinkedHashMap<>();
        int startPos = 0;
        int equalPos = -1;
        boolean needsDecode = false;
        for(int i = 0; i < newQueryString.length(); ++i) {
            char c = newQueryString.charAt(i);
            if(c == '=' && equalPos == -1) {
                equalPos = i;
            } else if(c == '&') {
                handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, i, encoding, needsDecode);
                needsDecode = false;
                startPos = i + 1;
                equalPos = -1;
            } else if((c == '%' || c == '+') && encoding != null) {
                needsDecode = true;
            }
        }
        if(startPos != newQueryString.length()) {
            handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, newQueryString.length(), encoding, needsDecode);
        }
        return newQueryParameters;
    }

    private static void handleQueryParameter(String newQueryString, Map<String, Deque<String>> newQueryParameters, int startPos, int equalPos, int i, final String encoding, boolean needsDecode) {
        String key;
        String value = "";
        if(equalPos == -1) {
            key = decodeParam(newQueryString, startPos, i, encoding, needsDecode);
        } else {
            key = decodeParam(newQueryString, startPos, equalPos, encoding, needsDecode);
            value = decodeParam(newQueryString, equalPos + 1, i, encoding, needsDecode);
        }

        Deque<String> queue = newQueryParameters.get(key);
        if (queue == null) {
            newQueryParameters.put(key, queue = new ArrayDeque<>(1));
        }
        if(value != null) {
            queue.add(value);
        }
    }

    private static String decodeParam(String newQueryString, int startPos, int equalPos, String encoding, boolean needsDecode) {
        String key;
        if (needsDecode) {
            try {
                key = URLDecoder.decode(newQueryString.substring(startPos, equalPos), encoding);
            } catch (UnsupportedEncodingException e) {
                key = newQueryString.substring(startPos, equalPos);
            }
        } else {
            key = newQueryString.substring(startPos, equalPos);
        }
        return key;
    }

    @Deprecated
    public static Map<String, Deque<String>> mergeQueryParametersWithNewQueryString(final Map<String, Deque<String>> queryParameters, final String newQueryString) {
        return mergeQueryParametersWithNewQueryString(queryParameters, newQueryString, StandardCharsets.UTF_8.name());
    }

    public static Map<String, Deque<String>> mergeQueryParametersWithNewQueryString(final Map<String, Deque<String>> queryParameters, final String newQueryString, final String encoding) {

        Map<String, Deque<String>> newQueryParameters = parseQueryString(newQueryString, encoding);
        //according to the spec the new query parameters have to 'take precedence'
        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            if (!newQueryParameters.containsKey(entry.getKey())) {
                newQueryParameters.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
            } else {
                newQueryParameters.get(entry.getKey()).addAll(entry.getValue());
            }
        }
        return newQueryParameters;
    }

    public static String getQueryParamEncoding(HttpServerExchange exchange) {
        String encoding = null;
        OptionMap undertowOptions = exchange.getConnection().getUndertowOptions();
        if(undertowOptions.get(UndertowOptions.DECODE_URL, true)) {
            encoding = undertowOptions.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
        }
        return encoding;
    }
}
