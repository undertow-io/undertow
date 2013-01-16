package io.undertow.security.api;

import java.security.Principal;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

/**
 * Interface that represents a persistent authenticated session.
 *
 * @author Stuart Douglas
 */
public interface AuthenticatedSessionManager {

    void userAuthenticated(final HttpServerExchange exchange, final Principal principal, final Account account);

    void userLoggedOut(final HttpServerExchange exchange, final Principal principal, final Account account);

    AuthenticationMechanism.AuthenticationMechanismResult lookupSession(final HttpServerExchange exchange, final IdentityManager identityManager);

}
