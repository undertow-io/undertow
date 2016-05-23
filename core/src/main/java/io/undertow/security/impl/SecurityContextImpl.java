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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult;
import io.undertow.security.api.AuthenticationMechanismContext;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContextImpl extends AbstractSecurityContext implements AuthenticationMechanismContext {


    private static final RuntimePermission PERMISSION = new RuntimePermission("MODIFY_UNDERTOW_SECURITY_CONTEXT");

    private AuthenticationState authenticationState = AuthenticationState.NOT_ATTEMPTED;
    private final AuthenticationMode authenticationMode;

    private String programaticMechName = "Programatic";

    /**
     * the authentication mechanisms. Note that in order to reduce the allocation of list and iterator structures
     * we use a custom linked list structure.
     */
    private Node<AuthenticationMechanism> authMechanisms = null;
    private final IdentityManager identityManager;

    public SecurityContextImpl(final HttpServerExchange exchange, final IdentityManager identityManager) {
        this(exchange, AuthenticationMode.PRO_ACTIVE, identityManager);
    }

    public SecurityContextImpl(final HttpServerExchange exchange, final AuthenticationMode authenticationMode, final IdentityManager identityManager) {
        super(exchange);
        this.authenticationMode = authenticationMode;
        this.identityManager = identityManager;
        if (System.getSecurityManager() != null) {
            System.getSecurityManager().checkPermission(PERMISSION);
        }
    }

    /*
     * Authentication can be represented as being at one of many states with different transitions depending on desired outcome.
     *
     * NOT_ATTEMPTED
     * ATTEMPTED
     * AUTHENTICATED
     * CHALLENGED_SENT
     */

    @Override
    public boolean authenticate() {
        UndertowLogger.SECURITY_LOGGER.debugf("Attempting to authenticate %s, authentication required: %s", exchange, isAuthenticationRequired());
        if(authenticationState == AuthenticationState.ATTEMPTED || (authenticationState == AuthenticationState.CHALLENGE_SENT && !exchange.isResponseStarted())) {
            //we are re-attempted, so we just reset the state
            //see UNDERTOW-263
            authenticationState = AuthenticationState.NOT_ATTEMPTED;
        }
        return !authTransition();
    }

    private boolean authTransition() {
        if (authTransitionRequired()) {
            switch (authenticationState) {
                case NOT_ATTEMPTED:
                    authenticationState = attemptAuthentication();
                    break;
                case ATTEMPTED:
                    authenticationState = sendChallenges();
                    break;
                default:
                    throw new IllegalStateException("It should not be possible to reach this.");
            }
            return authTransition();

        } else {
            // Keep in mind this switch statement is only called after a call to authTransitionRequired.
            switch (authenticationState) {
                case NOT_ATTEMPTED: // No constraint was set that mandated authentication so not reason to hold up the request.
                case ATTEMPTED: // Attempted based on incoming request but no a failure so allow the request to proceed.
                case AUTHENTICATED: // Authentication was a success - no responses sent.
                    return false;
                default:
                    // Remaining option is CHALLENGE_SENT to request processing must end.
                    return true;
            }
        }
    }

    private AuthenticationState attemptAuthentication() {
        return new AuthAttempter(authMechanisms,exchange).transition();
    }

    private AuthenticationState sendChallenges() {
        UndertowLogger.SECURITY_LOGGER.debugf("Sending authentication challenge for %s", exchange);
        return new ChallengeSender(authMechanisms, exchange).transition();
    }

    private boolean authTransitionRequired() {
        switch (authenticationState) {
            case NOT_ATTEMPTED:
                // There has been no attempt to authenticate the current request so do so either if required or if we are set to
                // be pro-active.
                return isAuthenticationRequired() || authenticationMode == AuthenticationMode.PRO_ACTIVE;
            case ATTEMPTED:
                // To be ATTEMPTED we know it was not AUTHENTICATED so if it is required we need to transition to send the
                // challenges.
                return isAuthenticationRequired();
            default:
                // At this point the state would either be AUTHENTICATED or CHALLENGE_SENT - either of which mean no further
                // transitions applicable for this request.
                return false;
        }
    }

    /**
     * Set the name of the mechanism used for authentication to be reported if authentication was handled programatically.
     *
     * @param programaticMechName
     */
    public void setProgramaticMechName(final String programaticMechName) {
        this.programaticMechName = programaticMechName;
    }

    @Override
    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        // TODO - Do we want to change this so we can ensure the mechanisms are not modifiable mid request?
        if(authMechanisms == null) {
            authMechanisms = new Node<>(handler);
        } else {
            Node<AuthenticationMechanism> cur = authMechanisms;
            while (cur.next != null) {
                cur = cur.next;
            }
            cur.next = new Node<>(handler);
        }
    }

    @Override
    @Deprecated
    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        List<AuthenticationMechanism> ret = new LinkedList<>();
        Node<AuthenticationMechanism> cur = authMechanisms;
        while (cur != null) {
            ret.add(cur.item);
            cur = cur.next;
        }
        return Collections.unmodifiableList(ret);
    }

    @Override
    @Deprecated
    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    @Override
    public boolean login(final String username, final String password) {

        UndertowLogger.SECURITY_LOGGER.debugf("Attempting programatic login for user %s for request %s", username, exchange);

        final Account account;
        if(System.getSecurityManager() == null) {
            account = identityManager.verify(username, new PasswordCredential(password.toCharArray()));
        } else {
            account = AccessController.doPrivileged(new PrivilegedAction<Account>() {
                @Override
                public Account run() {
                    return identityManager.verify(username, new PasswordCredential(password.toCharArray()));
                }
            });
        }

        if (account == null) {
            return false;
        }

        authenticationComplete(account, programaticMechName, true);
        this.authenticationState = AuthenticationState.AUTHENTICATED;

        return true;
    }

    @Override
    public void logout() {
        UndertowLogger.SECURITY_LOGGER.debugf("Logging out user %s for %s", getAuthenticatedAccount() , exchange);
        super.logout();
        this.authenticationState = AuthenticationState.NOT_ATTEMPTED;
    }


    private class AuthAttempter {

        private Node<AuthenticationMechanism> currentMethod;
        private final HttpServerExchange exchange;

        private AuthAttempter(Node<AuthenticationMechanism> currentMethod, final HttpServerExchange exchange) {
            this.exchange = exchange;
            this.currentMethod = currentMethod;
        }

        private AuthenticationState transition() {
            if (currentMethod != null) {
                final AuthenticationMechanism mechanism = currentMethod.item;
                currentMethod = currentMethod.next;
                AuthenticationMechanismOutcome outcome = mechanism.authenticate(exchange, SecurityContextImpl.this);
                UndertowLogger.SECURITY_LOGGER.debugf("Authentication outcome was %s with method %s for %s", outcome, mechanism, exchange);

                if (outcome == null) {
                    throw UndertowMessages.MESSAGES.authMechanismOutcomeNull();
                }

                switch (outcome) {
                    case AUTHENTICATED:
                        // TODO - Should verify that the mechanism did register an authenticated Account.
                        return AuthenticationState.AUTHENTICATED;
                    case NOT_AUTHENTICATED:
                        // A mechanism attempted to authenticate but could not complete, this now means that
                        // authentication is required and challenges need to be sent.
                        setAuthenticationRequired();
                        return AuthenticationState.ATTEMPTED;
                    case NOT_ATTEMPTED:
                        // Time to try the next mechanism.
                        return transition();
                    default:
                        throw new IllegalStateException();
                }

            } else {
                // Reached the end of the mechanisms and no mechanism authenticated for us to reach this point.
                return AuthenticationState.ATTEMPTED;
            }
        }

    }

    /**
     * Class responsible for sending the authentication challenges.
     */
    private class ChallengeSender {

        private Node<AuthenticationMechanism> currentMethod;
        private final HttpServerExchange exchange;

        private Integer chosenStatusCode = null;
        private boolean challengeSent = false;

        private ChallengeSender(Node<AuthenticationMechanism> currentMethod, final HttpServerExchange exchange) {
            this.exchange = exchange;
            this.currentMethod = currentMethod;
        }

        private AuthenticationState transition() {
            if (currentMethod != null) {
                final AuthenticationMechanism mechanism = currentMethod.item;
                currentMethod = currentMethod.next;
                ChallengeResult result = mechanism.sendChallenge(exchange, SecurityContextImpl.this);

                if (result.isChallengeSent()) {
                    challengeSent = true;
                    Integer desiredCode = result.getDesiredResponseCode();
                    if (desiredCode != null && (chosenStatusCode == null || chosenStatusCode.equals(StatusCodes.OK))) {
                        chosenStatusCode = desiredCode;
                        if (chosenStatusCode.equals(StatusCodes.OK) == false) {
                            if(!exchange.isResponseStarted()) {
                                exchange.setStatusCode(chosenStatusCode);
                            }
                        }
                    }
                }

                // We always transition so we can reach the end of the list and hit the else.
                return transition();

            } else {
                if(!exchange.isResponseStarted()) {
                    // Iterated all mechanisms, if OK it will not be set yet.
                    if (chosenStatusCode == null) {
                        if (challengeSent == false) {
                            // No mechanism generated a challenge so send a 403 as our challenge - i.e. just rejecting the request.
                            exchange.setStatusCode(StatusCodes.FORBIDDEN);
                        }
                    } else if (chosenStatusCode.equals(StatusCodes.OK)) {
                        exchange.setStatusCode(chosenStatusCode);
                    }
                }

                return AuthenticationState.CHALLENGE_SENT;
            }
        }

    }

    /**
     * Representation of the current authentication state of the SecurityContext.
     */
    enum AuthenticationState {
        NOT_ATTEMPTED,

        ATTEMPTED,

        AUTHENTICATED,

        CHALLENGE_SENT;
    }

    /**
     * To reduce allocations we use a custom linked list data structure
     * @param <T>
     */
    private static final class Node<T> {
        final T item;
        Node<T> next;

        private Node(T item) {
            this.item = item;
        }
    }

}
