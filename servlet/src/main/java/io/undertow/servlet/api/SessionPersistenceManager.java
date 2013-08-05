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

    public class PersistentSession {
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
