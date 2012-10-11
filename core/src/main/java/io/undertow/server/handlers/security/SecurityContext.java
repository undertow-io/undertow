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
package io.undertow.server.handlers.security;

import static io.undertow.server.handlers.security.AuthenticationState.NOT_REQUIRED;
import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.AttachmentKey;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xnio.IoFuture;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 * 
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContext {

    public static AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    private final List<AuthenticationMechanism> authenticationMechanisms = new ArrayList<AuthenticationMechanism>();

    private AuthenticationState authenticationState = NOT_REQUIRED;
    private Principal authenticatedPrincipal;

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise the
     * completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     * 
     * @param exchange The exchange
     * @param completionHandler The completion handler
     * @param nextHandler The next handler to invoke once auth succeeds
     */
    public void authenticate(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler,
            final HttpHandler nextHandler) {
        new AuthenticationRequest(authenticationMechanisms.iterator(), completionHandler, exchange, nextHandler).authenticate();
    }

    void setAuthenticationState(final AuthenticationState authenticationState) {
        this.authenticationState = authenticationState;
    }

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        authenticationMechanisms.add(handler);
    }

    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return Collections.unmodifiableList(authenticationMechanisms);
    }

    private class AuthenticationRequest {

        private final Iterator<AuthenticationMechanism> handlerIterator;
        private final HttpCompletionHandler completionHandler;
        private final HttpServerExchange exchange;
        private final HttpHandler nextHandler;

        private AuthenticationRequest(final Iterator<AuthenticationMechanism> handlerIterator,
                final HttpCompletionHandler completionHandler, final HttpServerExchange exchange, final HttpHandler nextHandler) {
            this.handlerIterator = handlerIterator;
            this.completionHandler = completionHandler;
            this.exchange = exchange;
            this.nextHandler = nextHandler;
        }

        void authenticate() {
            if (handlerIterator.hasNext()) {
                final AuthenticationMechanism handler = handlerIterator.next();
                IoFuture<AuthenticationMechanism.AuthenticationResult> resultFuture = handler.authenticate(exchange);
                resultFuture.addNotifier(new IoFuture.Notifier<AuthenticationMechanism.AuthenticationResult, Object>() {
                    @Override
                    public void notify(final IoFuture<? extends AuthenticationMechanism.AuthenticationResult> ioFuture,
                            final Object attachment) {
                        if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                            try {
                                AuthenticationMechanism.AuthenticationResult result = ioFuture.get();

                                if (result.getOutcome() == AUTHENTICATED) {
                                    SecurityContext.this.authenticatedPrincipal = result.getPrinciple();
                                    SecurityContext.this.authenticationState = AUTHENTICATED;
                                    HttpHandlers.executeHandler(nextHandler, exchange, AuthenticationRequest.this);
                                } else if (result.getOutcome() == NOT_AUTHENTICATED) {
                                    // no result, try the next handler
                                    authenticate();
                                } else {
                                    // We don't know how we got here so sending handleComplete probably has an incomplete set of
                                    // completion handlers.

                                    UndertowLogger.REQUEST_LOGGER.debug("authentication failed");
                                    exchange.setResponseCode(401);
                                    handleComplete();
                                }

                            } catch (IOException e) {
                                // will never happen, as state is DONE
                                new AllMechanismCompletionHandler(authenticationMechanisms.iterator(), exchange, completionHandler).handleComplete();
                            }
                        } else if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                            UndertowLogger.REQUEST_LOGGER.exceptionWhileAuthenticating(handler, ioFuture.getException());
                        } else if (ioFuture.getStatus() == IoFuture.Status.CANCELLED) {
                            // this should never happen
                            new AllMechanismCompletionHandler(authenticationMechanisms.iterator(), exchange, completionHandler).handleComplete();
                        }
                    }
                }, null);
            } else {
                // we have run through all auth mechanisms
                new AllMechanismCompletionHandler(authenticationMechanisms.iterator(), exchange, completionHandler).handleComplete();
            }
        }

    }

    private class AllMechanismCompletionHandler implements HttpCompletionHandler {

        private final Iterator<AuthenticationMechanism> handlerIterator;
        private final HttpServerExchange exchange;
        private final HttpCompletionHandler finalCompletionHandler;

        private AllMechanismCompletionHandler(Iterator<AuthenticationMechanism> handlerIterator, HttpServerExchange exchange,
                HttpCompletionHandler finalCompletionHandler) {
            this.handlerIterator = handlerIterator;
            this.exchange = exchange;
            this.finalCompletionHandler = finalCompletionHandler;
        }

        public void handleComplete() {
            if (handlerIterator.hasNext()) {
                handlerIterator.next().handleComplete(exchange, this);
            } else {
                finalCompletionHandler.handleComplete();
            }

        }

    }
}
