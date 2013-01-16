package io.undertow.security.api;

import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

/**
 * Interface that represents a persistent authenticated session.
 *
 * @author Stuart Douglas
 */
public interface AuthenticatedSessionManager {

    AuthenticationMechanism.AuthenticationResult lookupSession(final HttpServerExchange exchange, final IdentityManager identityManager);

}
