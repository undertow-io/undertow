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
package io.undertow.security.api;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;

import java.util.List;

/**
 * The security context.
 *
 * This context is attached to the exchange and holds all security related information.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @see io.undertow.security.impl.SecurityContextImpl
 */
public interface SecurityContext {

    /*
     * Methods Used To Run Authentication Process
     */

    /**
     * Performs authentication on the request.
     *
     * If authentication is REQUIRED then setAuthenticationRequired() should be called before calling this method.
     *
     * If the result indicates that a response has been sent to the client then no further attempts should be made to modify the
     * response. The caller of this method is responsible for ending the exchange.
     *
     * If this method returns <code>true</code> it can still have committed the response (e.g. form auth redirects back to the original
     * page). Callers should check that the exchange has not been ended before proceeding.
     *
     * @return <code>true</code> if either the request is successfully authenticated or if there is no failure validating the
     *         current request so that the request should continue to be processed, <code>false</code> if authentication was not
     *         completed and challenge has been prepared for the client.
     */
    boolean authenticate();

    /*
     * API for Direct Control of Authentication
     */

    /**
     * Attempts to log the user in using the provided credentials. This result will be stored in the current
     * {@link AuthenticatedSessionManager} (if any), so subsequent requests will automatically be authenticated
     * as this user.
     * <p>
     * This operation may block
     *
     * @param username The username
     * @param password The password
     * @return <code>true</code> if the login succeeded, false otherwise
     */
    boolean login(String username, String password);

    /**
     * de-authenticates the current exchange.
     *
     */
    void logout();

    /*
     * Methods Used To Control/Configure The Authentication Process.
     */

    /**
     * Marks this request as requiring authentication. Authentication challenge headers will only be sent if this
     * method has been called. If {@link #authenticate()}
     * is called without first calling this method then the request will continue as normal even if the authentication
     * was not successful.
     */
    void setAuthenticationRequired();

    /**
     * Returns true if authentication is required
     *
     * @return <code>true</code> If authentication is required
     */
    boolean isAuthenticationRequired();

    /**
     * Adds an authentication mechanism to this context. When {@link #authenticate()} is
     * called mechanisms will be iterated over in the order they are added, and given a chance to authenticate the user.
     *
     * @param mechanism The mechanism to add
     * @deprecated This method is now only applicable to {@code SecurityContext} implementations that also implement the {@link AuthenticationMechanismContext} interface.
     */
    @Deprecated(since="2.3.0", forRemoval=true)
    void addAuthenticationMechanism(AuthenticationMechanism mechanism);

    /**
     *
     * @return A list of all authentication mechanisms in this context
     * @deprecated Obtaining lists of mechanisms is discouraged, however there should not be a need to call this anyway.
     */
    @Deprecated(since="2.3.0", forRemoval=true)
    List<AuthenticationMechanism> getAuthenticationMechanisms();

    /*
     * Methods to access information about the current authentication status.
     */

    /**
     *
     * @return true if a user has been authenticated for this request, false otherwise.
     */
    boolean isAuthenticated();



    /**
     * Obtain the {@link Account} for the currently authenticated identity.
     *
     * @return The {@link Account} for the currently authenticated identity or <code>null</code> if no account is currently authenticated.
     */
    Account getAuthenticatedAccount();

    /**
     *
     * @return The name of the mechanism that was used to authenticate
     */
    String getMechanismName();

    /*
     * Methods Used by AuthenticationMechanism implementations.
     */

    /**
     * Obtain the associated {@link IdentityManager} to use to make account verification decisions.
     *
     * @return The associated {@link IdentityManager}
     * @deprecated Authentication mechanisms that rely on the {@link IdentityManager} should instead hold their own reference to it.
     */
    @Deprecated(since="2.3.0", forRemoval=true)
    IdentityManager getIdentityManager();

    /**
     * Called by the {@link AuthenticationMechanism} to indicate that an account has been successfully authenticated.
     *
     * Note: A successful verification of an account using the {@link IdentityManager} is not the same as a successful
     * authentication decision, other factors could be taken into account to make the final decision.
     *
     * @param account - The authenticated {@link Account}
     * @param mechanismName - The name of the mechanism used to authenticate the account.
     * @param cachingRequired - If this mechanism requires caching
     */
    void authenticationComplete(final Account account, final String mechanismName, final boolean cachingRequired);

    /**
     * Called by the {@link AuthenticationMechanism} to indicate that an authentication attempt has failed.
     *
     * This should only be called where an authentication attempt has truly failed, for authentication mechanisms where an
     * additional round trip with the client is expected this should not be called.
     *
     * Where possible the failure message should contain the name of the identity that authentication was being attempted for,
     * however as this is not always possible to identify in advance a generic message may be all that can be reported.
     *
     * @param message - The message describing the failure.
     * @param mechanismName - The name of the mechanism reporting the failure.
     */
    void authenticationFailed(final String message, final String mechanismName);

    /*
     * Methods for the management of NotificationHandler registrations.
     */

    /**
     * Register a {@link NotificationReceiver} interested in receiving notifications for security events that happen on this SecurityContext.
     *
     * @param receiver - The {@link NotificationReceiver} to register.
     */
    void registerNotificationReceiver(final NotificationReceiver receiver);

    /**
     * Remove a previously registered {@link NotificationReceiver} from this SecurityContext.
     *
     * If the supplied receiver has not been previously registered this method will fail silently.
     *
     * @param receiver - The {@link NotificationReceiver} to remove.
     */
    void removeNotificationReceiver(final NotificationReceiver receiver);
}
