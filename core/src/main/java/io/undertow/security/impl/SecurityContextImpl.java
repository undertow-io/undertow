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

import static io.undertow.UndertowMessages.MESSAGES;
import static io.undertow.util.StatusCodes.CODE_200;
import static io.undertow.util.StatusCodes.CODE_403;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationHandler;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.StatusCodes;
import io.undertow.util.WorkerDispatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import org.xnio.IoFuture;
import org.xnio.IoFuture.Notifier;

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
    private AuthenticationState authenticationState = AuthenticationState.NOT_ATTEMPTED;
    private final HttpServerExchange exchange;
    private final List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
    private final IdentityManager identityManager;
    private final Set<NotificationHandler> notificationHandler = new HashSet<NotificationHandler>();

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

    public IoFuture<Boolean> authenticate() {
        ConcreteIoFuture<Boolean> result = new ConcreteIoFuture<Boolean>();
        // TODO - I don't see a need to force single threaded - if this request is from the servlet APIs then the request will
        // have already been dispatched.
        authTransition(result);

        return result;
    }

    private void authTransition(final ConcreteIoFuture<Boolean> result) {
        if (authTransitionRequired()) {
            IoFuture<AuthenticationState> transitionResult = null;
            switch (authenticationState) {
                case NOT_ATTEMPTED:
                    transitionResult = attemptAuthentication();
                    break;
                case ATTEMPTED:
                    transitionResult = sendChallenges();
                    break;
                default:
                    throw new IllegalStateException("It should not be possible to reach this.");
            }
            transitionResult.addNotifier(new Notifier<AuthenticationState, Object>() {

                @Override
                public void notify(IoFuture<? extends AuthenticationState> ioFuture, Object attachment) {
                    // TODO - Could result contain an Exception?
                    try {
                        authenticationState = ioFuture.get();
                    } catch (IOException e) {
                        // TODO - Need a failure state I think to transition to.
                    }
                    authTransition(result);
                }
            }, null);

        } else {
            // Keep in mind this switch statement is only called after a call to authTransitionRequired.
            switch (authenticationState) {
                case NOT_ATTEMPTED: // No constraint was set that mandated authentication so not reason to hold up the request.
                case ATTEMPTED: // Attempted based on incoming request but no a failure so allow the request to proceed.
                case AUTHENITCATED: // Authentication was a success - no responses sent.
                    result.setResult(false);
                default:
                    // Remaining option is CHALLENGE_SENT to request processing must end.
                    result.setResult(true);
            }
        }
    }

    private IoFuture<AuthenticationState> attemptAuthentication() {
        ConcreteIoFuture<AuthenticationState> response = new ConcreteIoFuture<AuthenticationState>();

        new AuthAttempter(authMechanisms.iterator(), exchange, new WorkerDispatcherExecutor(exchange)).transition(response);

        return response;
    }

    private IoFuture<AuthenticationState> sendChallenges() {
        ConcreteIoFuture<AuthenticationState> response = new ConcreteIoFuture<AuthenticationState>();

        new ChallengeSender(authMechanisms.iterator(), exchange, new WorkerDispatcherExecutor(exchange)).transition(response);

        return response;
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
    public boolean isAuthenticated() {
        return authenticationState == AuthenticationState.AUTHENITCATED;
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

        authenticationComplete(account, "TODO", true);
        this.authenticationState = AuthenticationState.AUTHENITCATED;

        return true;
    }

    @Override
    public void logout() {
        sendNoticiation(new SecurityNotification(exchange, SecurityNotification.EventType.LOGGED_OUT, account, mechanismName,
                true, MESSAGES.userLoggedOut(account.getPrincipal().getName())));

        this.account = null;
        this.mechanismName = null;
        this.authenticationState = AuthenticationState.NOT_ATTEMPTED;
    }

    @Override
    public void authenticationComplete(Account account, String mechanism, boolean cacheable) {
        this.account = account;
        this.mechanismName = mechanism;

        sendNoticiation(new SecurityNotification(exchange, SecurityNotification.EventType.AUTHENTICATED, account, mechanism,
                cacheable, MESSAGES.userAuthenticated(account.getPrincipal().getName())));
    }

    private void sendNoticiation(final SecurityNotification notification) {
        synchronized (notificationHandler) {
            for (NotificationHandler current : notificationHandler) {
                current.handleNotification(notification);
            }
        }
    }

    @Override
    public void registerNotificationHandler(NotificationHandler handler) {
        synchronized (notificationHandler) {
            notificationHandler.add(handler);
        }
    }

    @Override
    public void removeNotificationHandler(NotificationHandler handler) {
        synchronized (notificationHandler) {
            notificationHandler.remove(handler);
        }
    }

    private class AuthAttempter {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;
        private final Executor handOffExecutor;

        private AuthAttempter(final Iterator<AuthenticationMechanism> mechanismIterator, final HttpServerExchange exchange,
                final Executor handOffExecutor) {
            this.mechanismIterator = mechanismIterator;
            this.exchange = exchange;
            this.handOffExecutor = handOffExecutor;
        }

        private void transition(final ConcreteIoFuture<AuthenticationState> authenticationState) {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                IoFuture<AuthenticationMechanismOutcome> mechanismResult = mechanism.authenticate(exchange,
                        SecurityContextImpl.this, handOffExecutor);
                mechanismResult.addNotifier(new Notifier<AuthenticationMechanismOutcome, Object>() {

                    @Override
                    public void notify(IoFuture<? extends AuthenticationMechanismOutcome> ioFuture, Object attachment) {
                        try {
                            AuthenticationMechanismOutcome outcome = ioFuture.get();
                            switch (outcome) {
                                case AUTHENTICATED:
                                    // TODO - Should verify that the mechanism did register an authenticated Account.
                                    authenticationState.setResult(AuthenticationState.AUTHENITCATED);
                                    break;
                                case NOT_AUTHENTICATED:
                                    // A mechanism attempted to authenticate but could not complete, this now means that
                                    // authentication is required and challenges need to be sent.
                                    setAuthenticationRequired();
                                    authenticationState.setResult(AuthenticationState.ATTEMPTED);
                                    break;
                                case NOT_ATTEMPTED:
                                    // Time to try the next mechanism.
                                    transition(authenticationState);
                                    break;
                            }
                        } catch (IOException e) {
                            // TODO - Something internal failed, probably want to to add a possible error state.
                            authenticationState.setResult(AuthenticationState.ATTEMPTED);
                        }

                    }
                }, null);

            } else {
                // Reached the end of the mechanisms and no mechanism authenticated for us to reach this point.
                authenticationState.setResult(AuthenticationState.ATTEMPTED);
            }
        }

    }

    /**
     * Class responsible for sending the authentication challenges.
     */
    private class ChallengeSender {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;
        private final Executor handOffExecutor;

        private boolean atLeastOneChallenge = false;
        private StatusCodes chosenStatusCode = null;

        private ChallengeSender(final Iterator<AuthenticationMechanism> mechanismIterator, final HttpServerExchange exchange,
                final Executor handOffExecutor) {
            this.mechanismIterator = mechanismIterator;
            this.exchange = exchange;
            this.handOffExecutor = handOffExecutor;
        }

        private void transition(final ConcreteIoFuture<AuthenticationState> authenticationState) {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                IoFuture<ChallengeResult> challengeResult = mechanism.sendChallenge(exchange, SecurityContextImpl.this,
                        handOffExecutor);
                challengeResult.addNotifier(new Notifier<ChallengeResult, Object>() {

                    @Override
                    public void notify(IoFuture<? extends ChallengeResult> ioFuture, Object attachment) {
                        try {
                            ChallengeResult result = ioFuture.get();
                            if (result.isChallengeSent()) {
                                atLeastOneChallenge = true;
                                StatusCodes desiredCode = result.getDesiredResponseCode();
                                if (chosenStatusCode == null) {
                                    chosenStatusCode = desiredCode;
                                } else if (desiredCode != null) {
                                    if (chosenStatusCode.equals(CODE_200)) {
                                        // Allows a more specific code to be chosen.
                                        // TODO - Still need a more complex code resolution strategy if many different codes are
                                        // returned (Although those mechanisms may just never work together.)
                                        chosenStatusCode = desiredCode;
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // TODO - Something about the exception - only sending a challenge at this point.
                        }

                        // We always transition so we can reach the end of the list and hit the else.
                        transition(authenticationState);
                    }
                }, null);

            } else {
                // Iterated all mechanisms, now need to select a suitable status code.
                if (atLeastOneChallenge) {
                    if (chosenStatusCode != null) {
                        exchange.setResponseCode(chosenStatusCode.getCode());
                    }
                } else {
                    // No mechanism generated a challenge so send a 403 as our challenge - i.e. just rejecting the request.
                    exchange.setResponseCode(CODE_403.getCode());
                }

                authenticationState.setResult(AuthenticationState.CHALLENGE_SENT);

            }
        }

    }

    /**
     * Representation of the current authentication state of the SecurityContext.
     */
    enum AuthenticationState {
      NOT_ATTEMPTED,

      ATTEMPTED,

      AUTHENITCATED,

      CHALLENGE_SENT;
    }

    private static final class WorkerDispatcherExecutor implements Executor {

        private final HttpServerExchange exchange;

        private WorkerDispatcherExecutor(final HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void execute(final Runnable command) {
            WorkerDispatcher.dispatch(exchange, command);
        }
    }



}
