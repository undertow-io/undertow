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
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * Enumeration of tokens expected in a HTTP Digest 'WWW_Authenticate' header.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum DigestWWWAuthenticateToken implements HeaderToken {

    REALM(Headers.REALM, true),
    DOMAIN(Headers.DOMAIN, true),
    NONCE(Headers.NONCE, true),
    OPAQUE(Headers.OPAQUE, true),
    STALE(Headers.STALE, false),
    ALGORITHM(Headers.ALGORITHM, false),
    MESSAGE_QOP(Headers.QOP, true),
    AUTH_PARAM(Headers.AUTH_PARAM, false);

    private static final HeaderTokenParser<DigestWWWAuthenticateToken> TOKEN_PARSER;

    static {
        Map<String, DigestWWWAuthenticateToken> expected = new LinkedHashMap<>(
                DigestWWWAuthenticateToken.values().length);
        for (DigestWWWAuthenticateToken current : DigestWWWAuthenticateToken.values()) {
            expected.put(current.getName(), current);
        }

        TOKEN_PARSER = new HeaderTokenParser<>(Collections.unmodifiableMap(expected));
    }

    private final String name;
    private final boolean quoted;

    DigestWWWAuthenticateToken(final HttpString name, final boolean quoted) {
        this.name = name.toString();
        this.quoted = quoted;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowQuoted() {
        return quoted;
    }

    public static Map<DigestWWWAuthenticateToken, String> parseHeader(final String header) {
        return TOKEN_PARSER.parseHeader(header);
    }

}
