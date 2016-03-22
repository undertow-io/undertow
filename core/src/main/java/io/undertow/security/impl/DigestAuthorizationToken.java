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

import io.undertow.util.HeaderToken;
import io.undertow.util.HeaderTokenParser;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enumeration of tokens expected in a HTTP Digest 'Authorization' header.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum DigestAuthorizationToken implements HeaderToken {

    USERNAME(Headers.USERNAME, true),
    REALM(Headers.REALM, true),
    NONCE(Headers.NONCE, true),
    DIGEST_URI(Headers.URI, true),
    RESPONSE(Headers.RESPONSE, true),
    ALGORITHM(Headers.ALGORITHM, true),
    CNONCE(Headers.CNONCE, true),
    OPAQUE(Headers.OPAQUE, true),
    MESSAGE_QOP(Headers.QOP, true),
    NONCE_COUNT(Headers.NONCE_COUNT, false),
    AUTH_PARAM(Headers.AUTH_PARAM, false);

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

    DigestAuthorizationToken(final HttpString name, final boolean quoted) {
        this.name = name.toString();
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
