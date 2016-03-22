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
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration to represent the Digest quality of protection options.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum DigestQop {

    AUTH("auth", false), AUTH_INT("auth-int", true);

    private static final Map<String, DigestQop> BY_TOKEN;

    static {
        DigestQop[] qops = DigestQop.values();

        Map<String, DigestQop> byToken = new HashMap<>(qops.length);
        for (DigestQop current : qops) {
            byToken.put(current.token, current);
        }

        BY_TOKEN = Collections.unmodifiableMap(byToken);
    }

    private final String token;
    private final boolean integrity;

    DigestQop(final String token, final boolean integrity) {
        this.token = token;
        this.integrity = integrity;
    }

    public String getToken() {
        return token;
    }

    public boolean isMessageIntegrity() {
        return integrity;
    }

    public static DigestQop forName(final String name) {
        return BY_TOKEN.get(name);
    }

}
