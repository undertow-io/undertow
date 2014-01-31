package io.undertow.servlet.api;

import io.undertow.server.session.SessionConfig;

/**
 * A class that allows the SessionConfig to be wrapped.
 *
 * This is generally used to append JVM route information to the session ID in clustered environments.
 *
 * @author Stuart Douglas
 */
public interface SessionConfigWrapper {

    SessionConfig wrap(final SessionConfig sessionConfig, final Deployment deployment);
}
