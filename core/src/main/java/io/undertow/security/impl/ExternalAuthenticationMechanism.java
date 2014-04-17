package io.undertow.security.impl;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.ExternalCredential;
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


    public static final Factory FACTORY = new Factory();
    public static final String NAME = "EXTERNAL";

    private final String name;

    public static final AttachmentKey<String> EXTERNAL_PRINCIPAL = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> EXTERNAL_AUTHENTICATION_TYPE = AttachmentKey.create(String.class);

    public ExternalAuthenticationMechanism(String name) {
        this.name = name;
    }
    public ExternalAuthenticationMechanism() {
        this(NAME);
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        String principal = exchange.getAttachment(EXTERNAL_PRINCIPAL);
        if(principal == null) {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
        Account account = securityContext.getIdentityManager().verify(principal, ExternalCredential.INSTANCE);
        if(account == null) {
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }
        String name = exchange.getAttachment(EXTERNAL_AUTHENTICATION_TYPE);
        securityContext.authenticationComplete(account, name != null ? name: this.name, false);

        return AuthenticationMechanismOutcome.AUTHENTICATED;
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return new ChallengeResult(false);
    }

    public static final class Factory implements AuthenticationMechanismFactory {

        private Factory() {}

        @Override
        public AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, Map<String, String> properties) {
            return new ExternalAuthenticationMechanism(mechanismName);
        }
    }
}
