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
package io.undertow.servlet.test.security.custom;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.security.ServletFormAuthenticationMechanism;
import io.undertow.util.Methods;

/**
 * <p>
 * Custom Authentication Mechanism has a slight change from the {@link FormAuthenticationMechanism} that the posting of
 * username/password happens to a resource ending with custom_security_check rather than j_security_check in the form
 * authentication.
 * </p>
 * <p>
 * This allows to test the injection of an {@link AuthenticationMechanism} to the {@link DeploymentManagerImpl} API
 * </p>
 *
 * @author anil saldhana
 * @since May 13, 2013
 */
public class CustomAuthenticationMechanism extends ServletFormAuthenticationMechanism {
    public static final String POST_LOCATION = "custom_security_check";

    public CustomAuthenticationMechanism(String name, String loginPage, String errorPage) {
        super(name, loginPage, errorPage);
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestURI().endsWith(POST_LOCATION) && exchange.getRequestMethod().equals(Methods.POST)) {
            return runFormAuth(exchange, securityContext);
        } else {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
    }
}