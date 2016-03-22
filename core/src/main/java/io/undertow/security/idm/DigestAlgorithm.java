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
package io.undertow.security.idm;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of the supported digest algorithms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum DigestAlgorithm {

    MD5("MD5", "MD5", false), MD5_SESS("MD5-sess", "MD5", true);

    private static final Map<String, DigestAlgorithm> BY_TOKEN;

    static {
        DigestAlgorithm[] algorithms = DigestAlgorithm.values();

        Map<String, DigestAlgorithm> byToken = new HashMap<>(algorithms.length);
        for (DigestAlgorithm current : algorithms) {
            byToken.put(current.token, current);
        }

        BY_TOKEN = Collections.unmodifiableMap(byToken);
    }

    private final String token;
    private final String digestAlgorithm;
    private final boolean session;

    DigestAlgorithm(final String token, final String digestAlgorithm, final boolean session) {
        this.token = token;
        this.digestAlgorithm = digestAlgorithm;
        this.session = session;
    }

    public String getToken() {
        return token;
    }

    public String getAlgorithm() {
        return digestAlgorithm;
    }

    public boolean isSession() {
        return session;
    }

    public MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(digestAlgorithm);
    }

    public static DigestAlgorithm forName(final String name) {
        return BY_TOKEN.get(name);
    }

}
