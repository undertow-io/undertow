/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.security.api;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.util.AttachmentKey;

import java.util.List;

import org.xnio.IoFuture;

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

    // TODO - Some of this is used within the core of undertow, some by the servlet integration and some by the mechanisms -
    // once released the use by mechanisms will require the greatest level of backwards compatibility maintenace so may be
    // better to split the rest out.

    /**
     * The attachment key that is used to attach this context to the exchange
     */
    AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

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
     * When this method is called depending on the authentication mechanisms and the current thread making the call the request
     * could occur in the same thread or be dispatched to a different thread, unless the caller is required to block it should
     * register a {@link IoFuture.Notifier} to handle the response.
     *
     * return {@link IoFuture<Boolean>} to indicate if a response has been sent to the calling client.
     */
    IoFuture<Boolean> authenticate();

    /*
     * API for Direct Control of Authentication
     */

    /**
     * Attempts to log the user in using the provided credentials. This result will be stored in the current
     * {@link AuthenticatedSessionManager} (if any), so subsequent requests will automatically be authenticated
     * as this user.
     * <p/>
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

    // TODO - May be better to pass a parameter to the authenticate methods to indicate that authentication is required.


    /**
     * Marks this request as requiring authentication. Authentication challenge headers will only be sent if this
     * method has been called. If {@link #authenticate(io.undertow.server.HttpCompletionHandler, io.undertow.server.HttpHandler)}
     * is called without first calling this method then the request will continue as normal even if the authentication
     * was not successful.
     */
    void setAuthenticationRequired();

    /**
     * Adds an authentication mechanism to this context. When {@link #authenticate()} is
     * called mechanisms will be iterated over in the order they are added, and given a chance to authenticate the user.
     *
     * @param mechanism The mechanism to add
     */
    void addAuthenticationMechanism(AuthenticationMechanism mechanism);

    /**
     *
     * @return A list of all authentication mechanisms in this context
     */
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
     */
    IdentityManager getIdentityManager();

    /**
     * Called by the {@link AuthenticationMechanism} to indicate that an account has been successfully authenticated.
     *
     * Note: A successful verification of an account using the {@link IdentityManager} is not the same as a successful
     * authentication decision, other factors could be taken into account to make the final decision.
     *
     * @param account - The authenticated {@link Account}
     * @param mechanismName - The name of the mechanism used to authenticate the account.
     * @param cacheable - Is the authentication cache-able i.e. can it be stored in a session to skip authentication for
     *        subsequent requests.
     */
    void authenticationComplete(final Account account, final String mechanismName, final boolean cacheable);

    // TODO - Should there be an authenticationFailed method that can be called by a mechanism for audit purposes to indicate that an authentication attempt failed.

    /*
     * Methods for the management of NotificationHandler registrations.
     */

    /**
     * Register a {@link NotificationHandler} interested in receiving notifications for security events that happen on this SecurityContext.
     *
     * @param handler - The {@link NotificationHandler} to register.
     */
    void registerNotificationHandler(final NotificationHandler handler);

    /**
     * Remove a previously registered {@link NotificationHandler} from this SecurityContext.
     *
     * If the supplied handler has not been previously registered this method will fail silently.
     *
     * @param handler - The {@link NotificationHandler} to remove.
     */
    void removeNotificationHandler(final NotificationHandler handler);

    class AuthenticationResult {

        private final AuthenticationMechanism.AuthenticationMechanismOutcome outcome;

        private final Runnable sendChallengeTask;



        public AuthenticationResult(final AuthenticationMechanism.AuthenticationMechanismOutcome outcome, final Runnable sendChallengeTask) {
            this.outcome = outcome;
            this.sendChallengeTask = sendChallengeTask;
        }

        public AuthenticationMechanism.AuthenticationMechanismOutcome getOutcome() {
            return outcome;
        }

        public Runnable getSendChallengeTask() {
            return sendChallengeTask;
        }
    }
}
