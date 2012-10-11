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

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;

import java.security.Principal;

import org.xnio.IoFuture;

/**
 * The interface to be implemented by a single authentication mechanism.
 *
 * The implementation of this interface are assumed to be stateless, if there is a need to share state between the authenticate
 * and handleComplete calls then it should be held in the HttpServerExchange.
 *
 * As an in-bound request is received the authenticate method is called on each mechanism in turn until one of the following
 * occurs: -
 *   - A mechanism successfully authenticates the incoming request.
 *   - A mechanism attempts but fails to authenticate the request.
 *   - The list of mechanisms is exhausted.
 *
 * This means that if the authenticate method is called on a mechanism it should assume it is required to check if it can actually
 * authenticate the incoming request, anything that would prevent it from performing the check would have already stopped the authenticate
 * method from being called.
 *
 * Authentication is allowed to proceed if either authentication was required AND one handler authenticated the request or it is
 * allowed to proceed if it is not required AND no handler failed to authenticate the request.
 *
 * The handleComplete methods are used as the request processing is returning up the chain, primarily these are used to
 * challenge the client to authenticate but where supported by the mechanism they could also be used to send mechanism specific
 * updates back with a request.
 *
 * If a mechanism successfully authenticated the incoming request then only the handleComplete method on that mechanism is
 * called.
 *
 * If any mechanism failed or if authentication was required and no mechanism succeeded in authenticating the request then
 * handleComplete will be called for all mechanisms.
 *
 * Finally if authentication was not required handleComplete will not be called for any of the mechanisms.
 *
 * The mechanisms will need to double check why handleComplete is being called, if the request was authenticated then they should
 * do nothing unless the mechanism has intermediate state to send back.  If the request was not authenticated then a challenge should
 * be sent.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface AuthenticationMechanism {

    IoFuture<AuthenticationResult> authenticate(final HttpServerExchange exchange);

    void handleComplete(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler);

    public enum AuthenticationOutcome {
        AUTHENTICATED, NOT_ATTEMPTED, FAILED;
    }

    public class AuthenticationResult {

        /**
         * The authenticated principle if this result was a success
         */
        private final Principal principle;

        /**
         * The result of the authentication call
         */
        private final AuthenticationOutcome outcome;

        // TODO - Should a mechanism be able to report using an Exception?

        public AuthenticationResult(final Principal principle, final AuthenticationOutcome outcome) {
            this.principle = principle;
            this.outcome = outcome;
        }

        public Principal getPrinciple() {
            return principle;
        }

        public AuthenticationOutcome getOutcome() {
            return outcome;
        }

    }

    class Util {

        static boolean shouldChallenge(final HttpServerExchange exchange) {
            SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);

            return context.getAuthenticationState() != AuthenticationState.AUTHENTICATED;
        }

    }

}
