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

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.ExternalCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.AttachmentKey;

import java.util.Map;

/**
 *
 * Authentication mechanism that uses an externally provided principal.
 *
 * WARNING: This method performs no verification. It must only be used if there is no
 * way for an end user to modify the principal, for example if Undertow is behind a
 * front end server that is responsible for authentication.
 *
 * @author Stuart Douglas
 */
public class ExternalAuthenticationMechanism implements AuthenticationMechanism {

    public static final AuthenticationMechanismFactory FACTORY = new Factory();

    public static final String NAME = "EXTERNAL";

    private final String name;
    private final IdentityManager identityManager;

    public static final AttachmentKey<String> EXTERNAL_PRINCIPAL = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> EXTERNAL_AUTHENTICATION_TYPE = AttachmentKey.create(String.class);

    public ExternalAuthenticationMechanism(String name, IdentityManager identityManager) {
        this.name = name;
        this.identityManager = identityManager;
    }

    public ExternalAuthenticationMechanism(String name) {
        this(name, null);
    }
    public ExternalAuthenticationMechanism() {
        this(NAME);
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        String principal = exchange.getAttachment(EXTERNAL_PRINCIPAL);
        if(principal == null) {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
        Account account = getIdentityManager(securityContext).verify(principal, ExternalCredential.INSTANCE);
        if(account == null) {
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }
        String name = exchange.getAttachment(EXTERNAL_AUTHENTICATION_TYPE);
        securityContext.authenticationComplete(account, name != null ? name: this.name, false);

        return AuthenticationMechanismOutcome.AUTHENTICATED;
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return ChallengeResult.NOT_SENT;
    }

    public static final class Factory implements AuthenticationMechanismFactory {

        @Deprecated
        public Factory(IdentityManager identityManager) {}

        public Factory() {}

        @Override
        public AuthenticationMechanism create(String mechanismName,IdentityManager identityManager, FormParserFactory formParserFactory, Map<String, String> properties) {
            return new ExternalAuthenticationMechanism(mechanismName, identityManager);
        }
    }
}
