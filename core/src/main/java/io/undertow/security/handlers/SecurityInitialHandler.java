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
package io.undertow.security.handlers;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityContextFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.SecurityContextFactoryImpl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * The security handler responsible for attaching the SecurityContext to the current {@link HttpServerExchange}.
 *
 * This handler is called early in the processing of the incoming request, subsequently supported authentication mechanisms will
 * be added to the context, a decision will then be made if authentication is required or optional and the associated mechanisms
 * will be called.
 *
 * In addition to the HTTPExchange authentication state can also be associated with the
 * {@link io.undertow.server.protocol.http.HttpServerConnection} and with the {@link io.undertow.server.session.Session} however this is
 * mechanism specific so it is down to the actual mechanisms to decide if there is state that can be re-used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@SuppressWarnings("deprecation")
public class SecurityInitialHandler extends AbstractSecurityContextAssociationHandler {

    private final AuthenticationMode authenticationMode;
    private final IdentityManager identityManager;
    private final String programaticMechName;
    private final SecurityContextFactory contextFactory;

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final String programaticMechName, final SecurityContextFactory contextFactory, final HttpHandler next) {
        super(next);
        this.authenticationMode = authenticationMode;
        this.identityManager = identityManager;
        this.programaticMechName = programaticMechName;
        this.contextFactory = contextFactory;
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final String programaticMechName, final HttpHandler next) {
        this(authenticationMode, identityManager, programaticMechName, SecurityContextFactoryImpl.INSTANCE, next);
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final HttpHandler next) {
        this(authenticationMode, identityManager, null, SecurityContextFactoryImpl.INSTANCE, next);
    }

    /**
     * @see io.undertow.security.handlers.AbstractSecurityContextAssociationHandler#createSecurityContext
     */
    @Override
    public SecurityContext createSecurityContext(final HttpServerExchange exchange) {
        return contextFactory.createSecurityContext(exchange, authenticationMode, identityManager, programaticMechName);
    }


}
