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

package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.QueryParameterUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The relative path
 *
 * @author Stuart Douglas
 */
public class RelativePathAttribute implements ExchangeAttribute {

    public static final String RELATIVE_PATH_SHORT = "%R";
    public static final String RELATIVE_PATH = "%{RELATIVE_PATH}";

    public static final ExchangeAttribute INSTANCE = new RelativePathAttribute();

    private static final String SLASH = "/";

    private RelativePathAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRelativePath();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        int pos = newValue.indexOf('?');
        String encoding = QueryParameterUtils.getQueryParamEncoding(exchange);
        final String path = pos == -1 ? newValue : newValue.substring(0, pos);
        final String decoded = decode(path, encoding);
        exchange.setRelativePath(decoded);
        final String requestURI = decode(exchange.getResolvedPath(), encoding) + decoded;
        exchange.setRequestURI(reencode(requestURI));
        exchange.setRequestPath(requestURI);

        if (pos != -1) {
            final String newQueryString = newValue.substring(pos);
            exchange.setQueryString(newQueryString);
            exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(newQueryString.substring(1), QueryParameterUtils.getQueryParamEncoding(exchange)));
        }
    }

    // the path might have inconsistent encoding so we try to decode it segment by segment
    private static String decode(String path, String encoding) {
        if (encoding == null) {
            return path;
        }
        return Arrays.stream(path.split(SLASH)).map(segment -> {
            try {
                return URLDecoder.decode(segment, encoding);
            } catch (IllegalArgumentException | UnsupportedEncodingException e) {
                return segment;
            }
        }).collect(Collectors.joining(SLASH));
    }

    // re-encode the previously decoded path, this ensures the segments aren't encoded twice
    private static String reencode(String decodedPath) {
        return Arrays.stream(decodedPath.split(SLASH)).map(segment -> {
            try {
                // URI.toASCIIString does percent-encoding " " -> "%20", whereas URIEncoder does " " -> "+"
                return new URI(null, null, segment, null).toASCIIString();
            } catch (URISyntaxException e) {
                return segment;
            }
        }).collect(Collectors.joining(SLASH));
    }

    @Override
    public String toString() {
        return RELATIVE_PATH;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Relative Path";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(RELATIVE_PATH) || token.equals(RELATIVE_PATH_SHORT) ? INSTANCE : null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
