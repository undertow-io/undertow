/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.security.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.undertow.UndertowMessages;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;

import static io.undertow.UndertowMessages.MESSAGES;
import static io.undertow.util.StatusCodes.FORBIDDEN;
import static io.undertow.util.StatusCodes.OK;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContextImpl implements SecurityContext {

    private static final RuntimePermission PERMISSION = new RuntimePermission("MODIFY_UNDERTOW_SECURITY_CONTEXT");

    private final AuthenticationMode authenticationMode;
    private boolean authenticationRequired;
    private String programaticMechName = "Programatic";
    private AuthenticationState authenticationState = AuthenticationState.NOT_ATTEMPTED;
    private final HttpServerExchange exchange;
    private final List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
    private final IdentityManager identityManager;
    private final List<NotificationReceiver> notificationReceivers = new ArrayList<NotificationReceiver>();


    // Maybe this will need to be a custom mechanism that doesn't exchange tokens with the client but will then
    // be configured to either associate with the connection, the session or some other arbitrary whatever.
    //
    // Do we want multiple to be supported or just one?  Maybe extend the AuthenticationMechanism to allow
    // it to be identified and called.

    private String mechanismName;
    private Account account;

    // TODO - Why two constructors?  Maybe the first can do.

    public SecurityContextImpl(final HttpServerExchange exchange, final IdentityManager identityManager) {
        this(exchange, AuthenticationMode.PRO_ACTIVE, identityManager);
    }

    public SecurityContextImpl(final HttpServerExchange exchange, final AuthenticationMode authenticationMode, final IdentityManager identityManager) {
        this.authenticationMode = authenticationMode;
        this.identityManager = identityManager;
        this.exchange = exchange;
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

    public boolean authenticate() {
        // TODO - I don't see a need to force single threaded - if this request is from the servlet APIs then the request will
        // have already been dispatched.
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
        return new AuthAttempter(authMechanisms.iterator(), exchange).transition();
    }

    private AuthenticationState sendChallenges() {
        return new ChallengeSender(authMechanisms.iterator(), exchange).transition();
    }

    private boolean authTransitionRequired() {
        switch (authenticationState) {
            case NOT_ATTEMPTED:
                // There has been no attempt to authenticate the current request so do so either if required or if we are set to
                // be pro-active.
                return authenticationRequired || authenticationMode == AuthenticationMode.PRO_ACTIVE;
            case ATTEMPTED:
                // To be ATTEMPTED we know it was not AUTHENTICATED so if it is required we need to transition to send the
                // challenges.
                return authenticationRequired;
            default:
                // At this point the state would either be AUTHENTICATED or CHALLENGE_SENT - either of which mean no further
                // transitions applicable for this request.
                return false;
        }
    }

    @Override
    public void setAuthenticationRequired() {
        authenticationRequired = true;
    }

    @Override
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticationState == AuthenticationState.AUTHENTICATED;
    }

    /**
     * Set the name of the mechanism used for authentication to be reported if authentication was handled programatically.
     *
     * @param programaticMechName
     */
    public void setProgramaticMechName(final String programaticMechName) {
        this.programaticMechName = programaticMechName;
    }

    /**
     * @return The name of the mechanism used to authenticate the request.
     */
    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    @Override
    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        // TODO - Do we want to change this so we can ensure the mechanisms are not modifiable mid request?
        authMechanisms.add(handler);
    }

    @Override
    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return Collections.unmodifiableList(authMechanisms);
    }

    @Override
    public Account getAuthenticatedAccount() {
        return account;
    }

    @Override
    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    @Override
    public boolean login(final String username, final String password) {
        final Account account = identityManager.verify(username, new PasswordCredential(password.toCharArray()));
        if (account == null) {
            return false;
        }

        authenticationComplete(account, programaticMechName, true);
        this.authenticationState = AuthenticationState.AUTHENTICATED;

        return true;
    }

    @Override
    public void logout() {
        if (!isAuthenticated()) {
            return;
        }
        sendNoticiation(new SecurityNotification(exchange, SecurityNotification.EventType.LOGGED_OUT, account, mechanismName, true,
                MESSAGES.userLoggedOut(account.getPrincipal().getName()), true));

        this.account = null;
        this.mechanismName = null;
        this.authenticationState = AuthenticationState.NOT_ATTEMPTED;
    }

    @Override
    public void authenticationComplete(Account account, String mechanism, final boolean cachingRequired) {
        authenticationComplete(account, mechanism, false, cachingRequired);
    }

    protected void authenticationComplete(Account account, String mechanism, boolean programatic, final boolean cachingRequired) {
        this.account = account;
        this.mechanismName = mechanism;

        sendNoticiation(new SecurityNotification(exchange, EventType.AUTHENTICATED, account, mechanism, programatic,
                MESSAGES.userAuthenticated(account.getPrincipal().getName()), cachingRequired));
    }

    @Override
    public void authenticationFailed(String message, String mechanism) {
        sendNoticiation(new SecurityNotification(exchange, EventType.FAILED_AUTHENTICATION, null, mechanism, false, message, true));
    }

    private void sendNoticiation(final SecurityNotification notification) {
        for (NotificationReceiver current : notificationReceivers) {
            current.handleNotification(notification);
        }
    }

    @Override
    public void registerNotificationReceiver(NotificationReceiver receiver) {
        notificationReceivers.add(receiver);
    }

    @Override
    public void removeNotificationReceiver(NotificationReceiver receiver) {
        notificationReceivers.remove(receiver);
    }

    private class AuthAttempter {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;

        private AuthAttempter(final Iterator<AuthenticationMechanism> mechanismIterator, final HttpServerExchange exchange) {
            this.mechanismIterator = mechanismIterator;
            this.exchange = exchange;
        }

        private AuthenticationState transition() {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                AuthenticationMechanismOutcome outcome = mechanism.authenticate(exchange, SecurityContextImpl.this);

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

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;

        private boolean atLeastOneChallenge = false;
        private Integer chosenStatusCode = null;

        private ChallengeSender(final Iterator<AuthenticationMechanism> mechanismIterator, final HttpServerExchange exchange) {
            this.mechanismIterator = mechanismIterator;
            this.exchange = exchange;
        }

        private AuthenticationState transition() {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                ChallengeResult result = mechanism.sendChallenge(exchange, SecurityContextImpl.this);

                if (result.isChallengeSent()) {
                    atLeastOneChallenge = true;
                    Integer desiredCode = result.getDesiredResponseCode();
                    if (chosenStatusCode == null) {
                        chosenStatusCode = desiredCode;
                    } else if (desiredCode != null) {
                        if (chosenStatusCode.equals(OK)) {
                            // Allows a more specific code to be chosen.
                            // TODO - Still need a more complex code resolution strategy if many different codes are
                            // returned (Although those mechanisms may just never work together.)
                            chosenStatusCode = desiredCode;
                        }
                    }
                }


                // We always transition so we can reach the end of the list and hit the else.
                return transition();

            } else {
                // Iterated all mechanisms, now need to select a suitable status code.
                if (atLeastOneChallenge) {
                    if (chosenStatusCode != null) {
                        exchange.setResponseCode(chosenStatusCode);
                    }
                } else {
                    // No mechanism generated a challenge so send a 403 as our challenge - i.e. just rejecting the request.
                    exchange.setResponseCode(FORBIDDEN);
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

}
