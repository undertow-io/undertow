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
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.io.Serializable;

/**
 * Interface that represents a persistent authenticated session.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface AuthenticatedSessionManager {

    /**
     * The attachment key that is used to attach the manager to the exchange
     */
    AttachmentKey<AuthenticatedSessionManager> ATTACHMENT_KEY = AttachmentKey.create(AuthenticatedSessionManager.class);

    AuthenticatedSession lookupSession(final HttpServerExchange exchange);

    void clearSession(HttpServerExchange exchange);

    class AuthenticatedSession implements Serializable {

        private final Account account;
        private final String mechanism;

        public AuthenticatedSession(final Account account, final String mechanism) {
            this.account = account;
            this.mechanism = mechanism;
        }

        public Account getAccount() {
            return account;
        }

        public String getMechanism() {
            return mechanism;
        }

    }

}
