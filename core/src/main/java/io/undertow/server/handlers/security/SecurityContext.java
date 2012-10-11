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

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.AttachmentKey;
import org.xnio.IoFuture;

import static io.undertow.server.handlers.security.AuthenticationState.AUTHENTICATED;
import static io.undertow.server.handlers.security.AuthenticationState.NOT_AUTHENTICATED;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityContext {

    // TODO - May reduce back to default
    public static AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);
    private final List<AuthenticationHandler> authenticationHandlers = new ArrayList<AuthenticationHandler>();

    private AuthenticationState authenticationState = NOT_AUTHENTICATED;
    private Principal authenticatedPrincipal;

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise
     * the completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify
     * the exchange.
     *
     * @param exchange          The exchange
     * @param completionHandler The completion handler
     * @param nextHandler       The next handler to invoke once auth succeeds
     */
    public void authenticate(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final HttpHandler nextHandler) {
        new Authentication(authenticationHandlers.iterator(), completionHandler, exchange, nextHandler).authenticate();
    }

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    public void addAuthenticationHandler(final AuthenticationHandler handler) {
        authenticationHandlers.add(handler);
    }

    public List<AuthenticationHandler> getAuthenticationHandlers() {
        return Collections.unmodifiableList(authenticationHandlers);
    }

    private class Authentication implements HttpCompletionHandler {

        private final Iterator<AuthenticationHandler> handlerIterator;
        private final HttpCompletionHandler completionHandler;
        private final HttpServerExchange exchange;
        private final HttpHandler nextHandler;
        private final List<Runnable> completionHandlerTasks = new ArrayList<Runnable>();

        private Authentication(final Iterator<AuthenticationHandler> handlerIterator, final HttpCompletionHandler completionHandler, final HttpServerExchange exchange, final HttpHandler nextHandler) {
            this.handlerIterator = handlerIterator;
            this.completionHandler = completionHandler;
            this.exchange = exchange;
            this.nextHandler = nextHandler;
        }

        void authenticate() {
            if (handlerIterator.hasNext()) {
                final AuthenticationHandler handler = handlerIterator.next();
                IoFuture<AuthenticationHandler.AuthenticationResult> resultFuture = handler.authenticate(exchange);
                resultFuture.addNotifier(new IoFuture.Notifier<AuthenticationHandler.AuthenticationResult, Object>() {
                    @Override
                    public void notify(final IoFuture<? extends AuthenticationHandler.AuthenticationResult> ioFuture, final Object attachment) {
                        if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                            try {
                                AuthenticationHandler.AuthenticationResult result = ioFuture.get();
                                if(result.getCompletionHandlerTask() != null) {
                                    completionHandlerTasks.add(result.getCompletionHandlerTask());
                                }
                                if(result.getResult() == AUTHENTICATED) {
                                    SecurityContext.this.authenticatedPrincipal = result.getPrinciple();
                                    SecurityContext.this.authenticationState = AUTHENTICATED;
                                    HttpHandlers.executeHandler(nextHandler, exchange, Authentication.this);
                                } else if(result.getResult() == NOT_AUTHENTICATED) {
                                    //no result, try the next handler
                                    authenticate();
                                } else {
                                    UndertowLogger.REQUEST_LOGGER.debug("authentication failed");
                                    exchange.setResponseCode(401);
                                    handleComplete();
                                }

                            } catch (IOException e) {
                                //will never happen, as state is DONE
                                handleComplete();
                            }
                        } else if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                            UndertowLogger.REQUEST_LOGGER.exceptionWhileAuthenticating(handler, ioFuture.getException());
                        } else if (ioFuture.getStatus() == IoFuture.Status.CANCELLED) {
                            //this should never happen
                            handleComplete();
                        }
                    }
                }, null);
            } else {
                //we have run through all auth mechanisms
                handleComplete();
            }
        }

        @Override
        public void handleComplete() {
            try {
                for (Runnable runnable : completionHandlerTasks) {
                    runnable.run();
                }
            } finally {
                completionHandler.handleComplete();
            }
        }
    }
}
