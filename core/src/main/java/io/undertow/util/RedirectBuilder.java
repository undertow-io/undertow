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

import io.undertow.server.HttpServerExchange;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Map;

/**
 * Utility class for building redirects.
 *
 * @author Stuart Douglas
 */
public class RedirectBuilder {

    public static final String UTF_8 = StandardCharsets.UTF_8.name();

    /**
     * Redirects to a new relative path. All other data from the exchange is preserved.
     *
     * @param exchange        The HTTP server exchange
     * @param newRelativePath The new relative path
     * @return
     */
    public static String redirect(final HttpServerExchange exchange, final String newRelativePath) {
        return redirect(exchange, newRelativePath, true);
    }

    /**
     * Redirects to a new relative path. All other data from the exchange is preserved.
     *
     * @param exchange          The HTTP server exchange
     * @param newRelativePath   The new relative path
     * @param includeParameters If query and path parameters from the exchange should be included
     * @return
     */
    public static String redirect(final HttpServerExchange exchange, final String newRelativePath, final boolean includeParameters) {
        try {
            StringBuilder uri = new StringBuilder(exchange.getRequestScheme());
            uri.append("://");
            uri.append(exchange.getHostAndPort());
            uri.append(encodeUrlPart(exchange.getResolvedPath()));
            if (exchange.getResolvedPath().endsWith("/")) {
                if (newRelativePath.startsWith("/")) {
                    uri.append(encodeUrlPart(newRelativePath.substring(1)));
                } else {
                    uri.append(encodeUrlPart(newRelativePath));
                }
            } else {
                if (!newRelativePath.startsWith("/")) {
                    uri.append('/');
                }
                uri.append(encodeUrlPart(newRelativePath));
            }
            if (includeParameters) {
                if (!exchange.getPathParameters().isEmpty()) {
                    boolean first = true;
                    uri.append(';');
                    for (Map.Entry<String, Deque<String>> param : exchange.getPathParameters().entrySet()) {
                        for (String value : param.getValue()) {
                            if (first) {
                                first = false;
                            } else {
                                uri.append('&');
                            }
                            uri.append(URLEncoder.encode(param.getKey(), UTF_8));
                            uri.append('=');
                            uri.append(URLEncoder.encode(value, UTF_8));
                        }
                    }
                }
                if (!exchange.getQueryString().isEmpty()) {
                    uri.append('?');
                    uri.append(exchange.getQueryString());
                }
            }
            return uri.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * perform URL encoding
     * <p>
     * TODO: this whole thing is kinda crappy.
     *
     * @return
     */
    private static String encodeUrlPart(final String part) throws UnsupportedEncodingException {
        //we need to go through and check part by part that a section does not need encoding

        int pos = 0;
        for (int i = 0; i < part.length(); ++i) {
            char c = part.charAt(i);
            if(c == '?') {
                break;
            } else if (c == '/') {
                if (pos != i) {
                    String original = part.substring(pos, i);
                    String encoded = URLEncoder.encode(original, UTF_8);
                    if (!encoded.equals(original)) {
                        return realEncode(part, pos);
                    }
                }
                pos = i + 1;
            } else if (c == ' ') {
                return realEncode(part, pos);
            }
        }
        return part;
    }

    private static String realEncode(String part, int startPos) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        sb.append(part.substring(0, startPos));
        int pos = startPos;
        for (int i = startPos; i < part.length(); ++i) {
            char c = part.charAt(i);
            if(c == '?') {
                break;
            } else if (c == '/') {
                if (pos != i) {
                    String original = part.substring(pos, i);
                    String encoded = URLEncoder.encode(original, UTF_8);
                    sb.append(encoded);
                    sb.append('/');
                    pos = i + 1;
                }
            }
        }

        String original = part.substring(pos);
        String encoded = URLEncoder.encode(original, UTF_8);
        sb.append(encoded);
        return sb.toString();
    }

    private RedirectBuilder() {

    }
}
