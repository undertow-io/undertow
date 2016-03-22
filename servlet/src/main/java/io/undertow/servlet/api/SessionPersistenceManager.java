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

package io.undertow.servlet.api;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * Interface that is used in development mode to support session persistence across redeploys.
 *
 * This is not intended for production use. Serialization is performed on a best effort basis and errors will be ignored.
 *
 * @author Stuart Douglas
 */
public interface SessionPersistenceManager {

    void persistSessions(final String deploymentName, Map<String, PersistentSession> sessionData);

    Map<String, PersistentSession> loadSessionAttributes(final String deploymentName, final ClassLoader classLoader);

    void clear(final String deploymentName);

    class PersistentSession {
        private final Date expiration;
        private final Map<String, Object> sessionData;

        public PersistentSession(Date expiration, Map<String, Object> sessionData) {
            this.expiration = expiration;
            this.sessionData = sessionData;
        }

        public Date getExpiration() {
            return expiration;
        }

        public Map<String, Object> getSessionData() {
            return Collections.unmodifiableMap(sessionData);
        }
    }

}
