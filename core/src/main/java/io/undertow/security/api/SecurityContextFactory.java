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

import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

/**
 * <p>
 * Interface that must be implemented by factories of {@link io.undertow.security.api.SecurityContext} instances.
 * </p>
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 * @deprecated Instead extend AbstractSecurityContextAssociationHandler to provide alternative contexts.
 */
@Deprecated(since="1.3.0", forRemoval=true)
public interface SecurityContextFactory {

    /**
     * <p>
     * Instantiates and returns a {@code SecurityContext} using the specified parameters.
     * </p>
     *
     * @param exchange the {@code HttpServerExchange} instance.
     * @param mode the {@code AuthenticationMode}.
     * @param identityManager the {@code IdentityManager} instance.
     * @param programmaticMechName a {@code String} representing the programmatic mechanism name. Can be null.
     * @return the constructed {@code SecurityContext} instance.
     */
    SecurityContext createSecurityContext(final HttpServerExchange exchange, final AuthenticationMode mode,
        final IdentityManager identityManager, final String programmaticMechName);
}
