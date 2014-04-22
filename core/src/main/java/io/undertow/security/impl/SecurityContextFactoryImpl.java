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

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityContextFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

/**
 * <p>
 * Default {@link io.undertow.security.api.SecurityContextFactory} implementation. It creates
 * {@link io.undertow.security.impl.SecurityContextImpl} instances with the specified parameters, setting the
 * programmatic mechanism name if it is not null.
 * </p>
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class SecurityContextFactoryImpl implements SecurityContextFactory {

    public static final SecurityContextFactory INSTANCE = new SecurityContextFactoryImpl();

    private SecurityContextFactoryImpl() {

    }

    @Override
    public SecurityContext createSecurityContext(final HttpServerExchange exchange, final AuthenticationMode mode,
        final IdentityManager identityManager, final String programmaticMechName) {
        SecurityContextImpl securityContext = SecurityActions.createSecurityContextImpl(exchange, mode, identityManager);
        if (programmaticMechName != null)
            securityContext.setProgramaticMechName(programmaticMechName);
        return securityContext;
    }
}
