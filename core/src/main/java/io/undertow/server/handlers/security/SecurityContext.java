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
package io.undertow.server.handlers.security;

import io.undertow.util.AttachmentKey;

import java.security.Principal;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityContext {

    static AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    private AuthenticationState authenticationState;
    private Principal authenticatedPrincipal;

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public void setAuthenticationState(AuthenticationState authenticationState) {
        this.authenticationState = authenticationState;
    }

    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    public void setAuthenticatedPrincipal(Principal authenticatedPrincipal) {
        this.authenticatedPrincipal = authenticatedPrincipal;
    }

}
