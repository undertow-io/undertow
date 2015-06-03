/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package io.undertow.security.api;


/**
 * An Undertow {@link SecurityContext} that uses Undertow {@link AuthenticationMechanism}
 * instances for authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface AuthenticationMechanismContext extends SecurityContext {

    /**
     * Adds an authentication mechanism to this context. When {@link #authenticate()} is
     * called mechanisms will be iterated over in the order they are added, and given a chance to authenticate the user.
     *
     * @param mechanism The mechanism to add
     */
    @Override
    void addAuthenticationMechanism(AuthenticationMechanism mechanism);

}
