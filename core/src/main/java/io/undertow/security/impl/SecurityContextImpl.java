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


import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationState;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.WorkerDispatcher;
import org.xnio.IoFuture;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContextImpl implements SecurityContext {

    private static final RuntimePermission PERMISSION = new RuntimePermission("MODIFY_UNDERTOW_SECURITY_CONTEXT");

    private static final Executor SAME_THREAD_EXECUTOR = new Executor() {
        @Override
        public void execute(final Runnable command) {
            command.run();
        }
    };

    private final HttpServerExchange exchange;
    private final List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
    private final IdentityManager identityManager;
    private final AuthenticatedSessionManager authenticatedSessionManager;


    // Maybe this will need to be a custom mechanism that doesn't exchange tokens with the client but will then
    // be configured to either associate with the connection, the session or some other arbitrary whatever.
    //
    // Do we want multiple to be supported or just one?  Maybe extend the AuthenticationMechanism to allow
    // it to be identified and called.

    private AuthenticationState authenticationState = AuthenticationState.NOT_REQUIRED;
    private Principal authenticatedPrincipal;
    private String mechanismName;
    private Account account;

    public SecurityContextImpl(final HttpServerExchange exchange, final IdentityManager identityManager) {
        this(exchange, identityManager, null);
    }

    public SecurityContextImpl(final HttpServerExchange exchange, final IdentityManager identityManager, final AuthenticatedSessionManager authenticatedSessionManager) {
        this.identityManager = identityManager;
        this.authenticatedSessionManager = authenticatedSessionManager;
        this.exchange = exchange;
        if (System.getSecurityManager() != null) {
            System.getSecurityManager().checkPermission(PERMISSION);
        }
    }


    @Override
    public AuthenticationResult authenticate() throws IOException {
        return new RequestAuthenticator(authMechanisms.iterator(), exchange, SAME_THREAD_EXECUTOR).authenticate().get();
    }


    @Override
    public IoFuture<AuthenticationResult> authenticate(final Executor executor) {
        return new RequestAuthenticator(authMechanisms.iterator(), exchange, executor).authenticate();
    }


    @Override
    public void authenticate(final HttpCompletionHandler completionHandler,
                             final HttpHandler nextHandler) {
        authenticate(new WorkerDispatcherExecutor(exchange))
                .addNotifier(new IoFuture.Notifier<AuthenticationResult, Object>() {
                    @Override
                    public void notify(final IoFuture<? extends AuthenticationResult> ioFuture, final Object o) {
                        try {
                            final AuthenticationResult result = ioFuture.get();

                            final RunnableCompletionHandler handler = new RunnableCompletionHandler(exchange, completionHandler, result.getRequestCompletionTasks());
                            if (result.getOutcome() == AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED) {
                                HttpHandlers.executeHandler(nextHandler, exchange, handler);
                            } else if (getAuthenticationState() == AuthenticationState.REQUIRED || result.getOutcome() == AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_AUTHENTICATED) {
                                handler.handleComplete();
                            } else {
                                HttpHandlers.executeHandler(nextHandler, exchange, handler);
                            }
                        } catch (IOException e) {
                            exchange.endExchange();
                        }
                    }
                }, null);
    }

    @Override
    public void setAuthenticationRequired() {
        authenticationState = AuthenticationState.REQUIRED;
    }

    @Override
    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    @Override
    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    /**
     * @return The name of the mechanism used to authenticate the request.
     */
    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    @Override
    public boolean isUserInGroup(String group) {
        return identityManager.isUserInGroup(account, group);
    }

    @Override
    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        authMechanisms.add(handler);
    }

    @Override
    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return Collections.unmodifiableList(authMechanisms);
    }

    @Override
    public boolean login(final String username, final String password) {
        final Account account = identityManager.lookupAccount(username);
        if (account == null) {
            return false;
        }
        if (!identityManager.verifyCredential(account, new PasswordCredential(password.toCharArray()))) {
            return false;
        }
        this.account = account;
        this.authenticationState = AuthenticationState.AUTHENTICATED;
        this.authenticatedPrincipal = new UndertowPrincipal(account);

        if (authenticatedSessionManager != null) {
            authenticatedSessionManager.userAuthenticated(exchange, authenticatedPrincipal, account);
        }
        return true;

    }

    @Override
    public void logout() {
        if (authenticatedSessionManager != null) {
            authenticatedSessionManager.userLoggedOut(exchange, authenticatedPrincipal, account);
        }
        this.account = null;
        this.authenticationState = AuthenticationState.NOT_REQUIRED;
        this.authenticatedPrincipal = null;
    }

    private class RequestAuthenticator {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;
        private final Executor handOffExecutor;

        private RequestAuthenticator(final Iterator<AuthenticationMechanism> handlerIterator, final HttpServerExchange exchange, final Executor handOffExecutor) {
            this.mechanismIterator = handlerIterator;
            this.exchange = exchange;
            this.handOffExecutor = handOffExecutor;
        }

        IoFuture<AuthenticationResult> authenticate() {

            final ConcreteIoFuture<AuthenticationResult> authResult = new ConcreteIoFuture<AuthenticationResult>();
            //first look for an existing authenticated session
            if (authenticatedSessionManager != null) {
                AuthenticationMechanism.AuthenticationMechanismResult result = authenticatedSessionManager.lookupSession(exchange, identityManager);
                if (result.getOutcome() == AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED) {

                    SecurityContextImpl.this.authenticatedPrincipal = result.getPrinciple();
                    SecurityContextImpl.this.mechanismName = "SESSION"; //TODO
                    SecurityContextImpl.this.account = result.getAccount();
                    SecurityContextImpl.this.authenticationState = AuthenticationState.AUTHENTICATED;

                    authResult.setResult(new AuthenticationResult(AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED, new Runnable() {
                        @Override
                        public void run() {

                        }
                    }));
                    return authResult;
                }
            }
            authenticate(authResult);
            return authResult;
        }

        private void authenticate(final ConcreteIoFuture<AuthenticationResult> authResult) {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                IoFuture<AuthenticationMechanism.AuthenticationMechanismResult> resultFuture = mechanism.authenticate(exchange, identityManager, handOffExecutor);
                resultFuture.addNotifier(new IoFuture.Notifier<AuthenticationMechanism.AuthenticationMechanismResult, Object>() {
                    @Override
                    public void notify(final IoFuture<? extends AuthenticationMechanism.AuthenticationMechanismResult> ioFuture,
                                       final Object attachment) {
                        try {
                            if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                                AuthenticationMechanism.AuthenticationMechanismResult result = ioFuture.get();
                                switch (result.getOutcome()) {
                                    case AUTHENTICATED:
                                        SecurityContextImpl.this.authenticatedPrincipal = result.getPrinciple();
                                        SecurityContextImpl.this.mechanismName = mechanism.getName();
                                        SecurityContextImpl.this.account = result.getAccount();
                                        SecurityContextImpl.this.authenticationState = AuthenticationState.AUTHENTICATED;

                                        if(result.isRequiresSession()) {
                                            authenticatedSessionManager.userAuthenticated(exchange, result.getPrinciple(), result.getAccount());
                                        }

                                        Runnable singleComplete = new SingleMechanismCompletionTask(mechanism, exchange);
                                        authResult.setResult(new AuthenticationResult(AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED, singleComplete));
                                        break;
                                    case NOT_ATTEMPTED:
                                        // That mechanism didn't attempt at all so see if there is another mechanism to try.
                                        authenticate(authResult);
                                        break;
                                    default:
                                        UndertowLogger.REQUEST_LOGGER.debug("authentication not complete, sending challenges.");


                                        // Either authentication failed or the mechanism is in an intermediate state and
                                        // requires
                                        // an additional round trip with the client - either way all mechanisms must now
                                        // complete.
                                        authResult.setResult(new AuthenticationResult(AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_AUTHENTICATED,
                                                new AllMechanismCompletionTask(authMechanisms.iterator(), exchange)));
                                        break;
                                }
                            } else if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                                UndertowLogger.REQUEST_LOGGER.exceptionWhileAuthenticating(mechanism, ioFuture.getException());
                                authResult.setException(ioFuture.getException());
                            } else if (ioFuture.getStatus() == IoFuture.Status.CANCELLED) {
                                authResult.setException(new IOException());
                            }
                        } catch (IOException e) {
                            authResult.setException(e);
                        }
                    }
                }, null);
            } else {
                authResult.setResult(new AuthenticationResult(AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_ATTEMPTED, new AllMechanismCompletionTask(authMechanisms.iterator(), exchange)));
            }

        }
    }

    /**
     * A {@link HttpCompletionHandler} that is used when
     * {@link AuthenticationMechanism#handleComplete(HttpServerExchange, HttpCompletionHandler)} need to be called on each
     * {@link AuthenticationMechanism} in turn.
     */
    private class AllMechanismCompletionTask implements Runnable {

        private final Iterator<AuthenticationMechanism> handlerIterator;
        private final HttpServerExchange exchange;

        private AllMechanismCompletionTask(Iterator<AuthenticationMechanism> handlerIterator, HttpServerExchange exchange) {
            this.handlerIterator = handlerIterator;
            this.exchange = exchange;
        }

        public void run() {
            while (handlerIterator.hasNext()) {
                handlerIterator.next().sendChallenge(exchange);
            }
        }
    }

    private class SingleMechanismCompletionTask implements Runnable {

        private final AuthenticationMechanism mechanism;
        private final HttpServerExchange exchange;

        private SingleMechanismCompletionTask(AuthenticationMechanism mechanism, HttpServerExchange exchange) {
            this.mechanism = mechanism;
            this.exchange = exchange;
        }

        public void run() {
            mechanism.sendChallenge(exchange);
        }
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


    private static final class RunnableCompletionHandler implements HttpCompletionHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final Runnable runnable;

        private RunnableCompletionHandler(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final Runnable runnable) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.runnable = runnable;
        }

        @Override
        public void handleComplete() {
            try {
                if (!exchange.isResponseStarted()) {
                    runnable.run();
                }
            } finally {
                exchange.endExchange();
            }
        }
    }

}
