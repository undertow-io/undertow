package io.undertow.util;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.server.handlers.form.FormParserFactory;

import java.util.Map;

/**
 * {@link AuthenticationMechanismFactory} that simply returns a pre configured {@link AuthenticationMechanism}
 * @author Stuart Douglas
 */
public class ImmediateAuthenticationMechanismFactory implements AuthenticationMechanismFactory {

    private final AuthenticationMechanism authenticationMechanism;

    public ImmediateAuthenticationMechanismFactory(AuthenticationMechanism authenticationMechanism) {
        this.authenticationMechanism = authenticationMechanism;
    }

    @Override
    public AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, Map<String, String> properties) {
        return authenticationMechanism;
    }
}
