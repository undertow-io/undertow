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

import java.security.Principal;

import io.undertow.server.HttpServerExchange;
import org.xnio.IoFuture;

/**
 * @author Stuart Douglas
 */
public interface AuthenticationHandler {

    IoFuture<AuthenticationResult> authenticate(final HttpServerExchange exchange);

    public class AuthenticationResult {

        /**
         * The authenticated principle if this result was a success
         */
        private final Principal principle;

        /**
         * The result of the authentication
         */
        private final AuthenticationState result;

        /**
         * A task to be run at completion handler time
         */
        private final Runnable completionHandlerTask;

        public AuthenticationResult(final Principal principle, final AuthenticationState result, final Runnable completionHandlerTask) {
            this.principle = principle;
            this.result = result;
            this.completionHandlerTask = completionHandlerTask;
        }

        public Principal getPrinciple() {
            return principle;
        }

        public AuthenticationState getResult() {
            return result;
        }

        public Runnable getCompletionHandlerTask() {
            return completionHandlerTask;
        }
    }

}
