package io.undertow.predicate;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Predicate that returns true if authentication is required.
 *
 * @author Stuart Douglas
 */
public class AuthenticationRequiredPredicate implements Predicate {

    public static final AuthenticationRequiredPredicate INSTANCE = new AuthenticationRequiredPredicate();

    @Override
    public boolean resolve(HttpServerExchange value) {
        SecurityContext sc = value.getSecurityContext();
        if(sc == null) {
            return false;
        }
        return sc.isAuthenticationRequired();
    }

    @Override
    public String toString() {
        return "auth-required()";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "auth-required";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<>();
            return params;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            return INSTANCE;
        }
    }
}
