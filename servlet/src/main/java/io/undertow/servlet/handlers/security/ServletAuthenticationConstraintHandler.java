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
package io.undertow.servlet.handlers.security;

import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.servlet.api.SecurityInfo.EmptyRoleSemantic;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * A simple handler that just sets the auth type to REQUIRED after iterating each of the {@link SingleConstraintMatch} instances
 * and identifying if any require authentication.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServletAuthenticationConstraintHandler extends AuthenticationConstraintHandler {

    public ServletAuthenticationConstraintHandler(final HttpHandler next) {
        super(next);
    }

    @Override
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        //j_security_check always requires auth
        if (exchange.getRelativePath().endsWith(ServletFormAuthenticationMechanism.DEFAULT_POST_LOCATION)) {
            return true;
        }
        List<SingleConstraintMatch> constraints = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getRequiredConstrains();

        /*
         * Even once this is set to true the reason we allow the loop to continue is in case an empty role with a semantic of
         * deny is found as that will override everything.
         */
        boolean authenticationRequired = false;
        for (SingleConstraintMatch constraint : constraints) {
            if (constraint.getRequiredRoles().isEmpty()) {
                if (constraint.getEmptyRoleSemantic() == EmptyRoleSemantic.DENY) {
                    /*
                     * For this case we return false as we know it can never be satisfied.
                     */
                    return false;
                } else if (constraint.getEmptyRoleSemantic() == EmptyRoleSemantic.AUTHENTICATE) {
                    authenticationRequired = true;
                }
            } else {
                authenticationRequired = true;
            }
        }
        if(authenticationRequired) {
            UndertowLogger.SECURITY_LOGGER.debugf("Authenticating required for request %s", exchange);
        }
        return authenticationRequired;
    }

}
