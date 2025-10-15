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
import java.util.Collections;
import java.util.List;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class ETagUtils {

    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final char W = 'W';
    private static final char SLASH = '/';

    /**
     * Handles the if-match header. returns true if the request should proceed, false otherwise
     *
     * @param exchange the exchange
     * @param etag    The etags
     * @return
     */
    public static boolean handleIfMatch(final HttpServerExchange exchange, final ETag etag, boolean allowWeak) {
        return handleIfMatch(exchange, Collections.singletonList(etag), allowWeak);
    }

    /**
     * Handles the if-match header. returns true if the request should proceed, false otherwise
     *
     * @param exchange the exchange
     * @param etags    The etags
     * @return
     */
    public static boolean handleIfMatch(final HttpServerExchange exchange, final List<ETag> etags, boolean allowWeak) {
        return handleIfMatch(exchange.getRequestHeaders().getFirst(Headers.IF_MATCH), etags, allowWeak);
    }

    /**
     * Handles the if-match header. returns true if the request should proceed, false otherwise
     *
     * @param ifMatch The if match header
     * @param etag   The etags
     * @return
     */
    public static boolean handleIfMatch(final String ifMatch, final ETag etag, boolean allowWeak) {
        return handleIfMatch(ifMatch, Collections.singletonList(etag), allowWeak);
    }

    /**
     * Handles the if-match header. returns true if the request should proceed, false otherwise
     *
     * @param ifMatch The ifMatch header
     * @param etags   The etags
     * @return
     */
    public static boolean handleIfMatch(final String ifMatch, final List<ETag> etags, boolean allowWeak) {
        if (ifMatch == null) {
            return true;
        }
        if (ifMatch.equals("*")) {
            return true; //todo: how to tell if there is a current entity for the request
        }
        List<ETag> parts = parseETagList(ifMatch);
        for (ETag part : parts) {
            if (part.isWeak() && !allowWeak) {
                continue;
            }
            for (ETag tag : etags) {
                if (tag != null) {
                    if (tag.isWeak() && !allowWeak) {
                        continue;
                    }
                    if (tag.getTag().equals(part.getTag())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * Handles the if-none-match header. returns true if the request should proceed, false otherwise
     *
     * @param exchange the exchange
     * @param etag    The etags
     * @return
     */
    public static boolean handleIfNoneMatch(final HttpServerExchange exchange, final ETag etag, boolean allowWeak) {
        return handleIfNoneMatch(exchange, Collections.singletonList(etag), allowWeak);
    }

    /**
     * Handles the if-none-match header. returns true if the request should proceed, false otherwise
     *
     * @param exchange the exchange
     * @param etags    The etags
     * @return
     */
    public static boolean handleIfNoneMatch(final HttpServerExchange exchange, final List<ETag> etags, boolean allowWeak) {
        return handleIfNoneMatch(exchange.getRequestHeaders().getFirst(Headers.IF_NONE_MATCH), etags, allowWeak);
    }

    /**
     * Handles the if-none-match header. returns true if the request should proceed, false otherwise
     *
     * @param ifNoneMatch the header
     * @param etag       The etags
     * @return
     */
    public static boolean handleIfNoneMatch(final String ifNoneMatch, final ETag etag, boolean allowWeak) {
        return handleIfNoneMatch(ifNoneMatch, Collections.singletonList(etag), allowWeak);
    }

    /**
     * Handles the if-none-match header. returns true if the request should proceed, false otherwise
     *
     * @param ifNoneMatch the header
     * @param etags       The etags
     * @return
     */
    public static boolean handleIfNoneMatch(final String ifNoneMatch, final List<ETag> etags, boolean allowWeak) {
        if (ifNoneMatch == null) {
            return true;
        }
        List<ETag> parts = parseETagList(ifNoneMatch);
        for (ETag part : parts) {
            if (part.getTag().equals("*")) {
                return false;
            }
            if (part.isWeak() && !allowWeak) {
                continue;
            }
            for (ETag tag : etags) {
                if (tag != null) {
                    if (tag.isWeak() && !allowWeak) {
                        continue;
                    }
                    if (tag.getTag().equals(part.getTag())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<ETag> parseETagList(final String header) {
        char[] headerChars = header.toCharArray();

        // The LinkedHashMap is used so that the parameter order can also be retained.
        List<ETag> response = new ArrayList<>();

        SearchingFor searchingFor = SearchingFor.START_OF_VALUE;
        int valueStart = 0;
        boolean weak = false;
        boolean malformed = false;

        for (int i = 0; i < headerChars.length; i++) {
            switch (searchingFor) {
                case START_OF_VALUE:
                    if (headerChars[i] != COMMA && !Character.isWhitespace(headerChars[i])) {
                        if (headerChars[i] == QUOTE) {
                            valueStart = i + 1;
                            searchingFor = SearchingFor.LAST_QUOTE;
                            weak = false;
                            malformed = false;
                        } else if (headerChars[i] == W) {
                            searchingFor = SearchingFor.WEAK_SLASH;
                        }
                    }
                    break;
                case WEAK_SLASH:
                    if (headerChars[i] == QUOTE) {
                        valueStart = i + 1;
                        searchingFor = SearchingFor.LAST_QUOTE;
                        weak = true;
                        malformed = false;
                    } else if (headerChars[i] != SLASH) {
                        malformed = true;
                        searchingFor = SearchingFor.END_OF_VALUE;
                    }
                    break;
                case LAST_QUOTE:
                    if (headerChars[i] == QUOTE) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        response.add(new ETag(weak, value.trim()));
                        searchingFor = SearchingFor.START_OF_VALUE;
                    }
                    break;
                case END_OF_VALUE:
                    if (headerChars[i] == COMMA || Character.isWhitespace(headerChars[i])) {
                        if (!malformed) {
                            String value = String.valueOf(headerChars, valueStart, i - valueStart);
                            response.add(new ETag(weak, value.trim()));
                            searchingFor = SearchingFor.START_OF_VALUE;
                        }
                    }
                    break;
            }
        }

        if (searchingFor == SearchingFor.END_OF_VALUE || searchingFor == SearchingFor.LAST_QUOTE) {
            if (!malformed) {
                // Special case where we reached the end of the array containing the header values.
                String value = String.valueOf(headerChars, valueStart, headerChars.length - valueStart);
                response.add(new ETag(weak, value.trim()));
            }
        }

        return response;
    }

    /**
     * @param exchange The exchange
     * @return The ETag for the exchange, or null if the etag is not set
     */
    public static ETag getETag(final HttpServerExchange exchange) {
        final String tag = exchange.getResponseHeaders().getFirst(Headers.ETAG);
        if (tag == null) {
            return null;
        }
        char[] headerChars = tag.toCharArray();
        SearchingFor searchingFor = SearchingFor.START_OF_VALUE;
        int valueStart = 0;
        boolean weak = false;
        boolean malformed = false;
        for (int i = 0; i < headerChars.length; i++) {
            switch (searchingFor) {
                case START_OF_VALUE:
                    if (headerChars[i] != COMMA && !Character.isWhitespace(headerChars[i])) {
                        if (headerChars[i] == QUOTE) {
                            valueStart = i + 1;
                            searchingFor = SearchingFor.LAST_QUOTE;
                            weak = false;
                            malformed = false;
                        } else if (headerChars[i] == W) {
                            searchingFor = SearchingFor.WEAK_SLASH;
                        }
                    }
                    break;
                case WEAK_SLASH:
                    if (headerChars[i] == QUOTE) {
                        valueStart = i + 1;
                        searchingFor = SearchingFor.LAST_QUOTE;
                        weak = true;
                        malformed = false;
                    } else if (headerChars[i] != SLASH) {
                        return null; //malformed
                    }
                    break;
                case LAST_QUOTE:
                    if (headerChars[i] == QUOTE) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        return new ETag(weak, value.trim());
                    }
                    break;
                case END_OF_VALUE:
                    if (headerChars[i] == COMMA || Character.isWhitespace(headerChars[i])) {
                        if (!malformed) {
                            String value = String.valueOf(headerChars, valueStart, i - valueStart);
                            return new ETag(weak, value.trim());
                        }
                    }
                    break;
            }
        }
        if (searchingFor == SearchingFor.END_OF_VALUE || searchingFor == SearchingFor.LAST_QUOTE) {
            if (!malformed) {
                // Special case where we reached the end of the array containing the header values.
                String value = String.valueOf(headerChars, valueStart, headerChars.length - valueStart);
                return new ETag(weak, value.trim());
            }
        }

        return null;
    }

    enum SearchingFor {
        START_OF_VALUE, LAST_QUOTE, END_OF_VALUE, WEAK_SLASH;
    }
}
