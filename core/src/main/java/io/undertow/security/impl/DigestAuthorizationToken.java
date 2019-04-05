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
package io.undertow.security.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.undertow.util.HeaderToken;
import io.undertow.util.HeaderTokenParser;
import io.undertow.util.HttpHeaderNames;

/**
 * Enumeration of tokens expected in a HTTP Digest 'Authorization' header.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum DigestAuthorizationToken implements HeaderToken {

    USERNAME(HttpHeaderNames.USERNAME, true),
    REALM(HttpHeaderNames.REALM, true),
    NONCE(HttpHeaderNames.NONCE, true),
    DIGEST_URI(HttpHeaderNames.URI, true),
    RESPONSE(HttpHeaderNames.RESPONSE, true),
    ALGORITHM(HttpHeaderNames.ALGORITHM, true),
    CNONCE(HttpHeaderNames.CNONCE, true),
    OPAQUE(HttpHeaderNames.OPAQUE, true),
    MESSAGE_QOP(HttpHeaderNames.QOP, true),
    NONCE_COUNT(HttpHeaderNames.NONCE_COUNT, false),
    AUTH_PARAM(HttpHeaderNames.AUTH_PARAM, false);

    private static final HeaderTokenParser<DigestAuthorizationToken> TOKEN_PARSER;

    static {
        Map<String, DigestAuthorizationToken> expected = new LinkedHashMap<>(
                DigestAuthorizationToken.values().length);
        for (DigestAuthorizationToken current : DigestAuthorizationToken.values()) {
            expected.put(current.getName(), current);
        }

        TOKEN_PARSER = new HeaderTokenParser<>(Collections.unmodifiableMap(expected));
    }

    private final String name;
    private final boolean quoted;

    DigestAuthorizationToken(final String name, final boolean quoted) {
        this.name = name;
        this.quoted = quoted;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAllowQuoted() {
        return quoted;
    }

    public static Map<DigestAuthorizationToken, String> parseHeader(final String header) {
        return TOKEN_PARSER.parseHeader(header);
    }
}
