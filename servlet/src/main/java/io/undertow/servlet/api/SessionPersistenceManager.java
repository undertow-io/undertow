package io.undertow.servlet.api;

import java.util.Map;

/**
 * Interface that is used in development mode to support session persistence across redeploys.
 *
 * This is not intended for production use. Serialization is performed on a best effort basis and errors will be ignored.
 *
 * @author Stuart Douglas
 */
public interface SessionPersistenceManager {

    void persistSessions(final String deploymentName, Map<String, Map<String, Object>> sessionData);

    Map<String, Map<String, Object>> loadSessionAttributes(final String deploymentName, final ClassLoader classLoader);

    void clear(final String deploymentName);

}
