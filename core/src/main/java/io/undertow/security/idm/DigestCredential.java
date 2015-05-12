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
package io.undertow.security.idm;

/**
 * An extension of {@link Credential} to provide some additional methods needed to enable verification of a request where
 * {@link io.undertow.security.impl.DigestAuthenticationMechanism} is in use.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface DigestCredential extends Credential {

    /**
     * Obtain the selected {@link DigestAlgorithm} for the request being authenticated.
     *
     * @return The {@link DigestAlgorithm} for the request being authenticated.
     */
    DigestAlgorithm getAlgorithm();

    /**
     * Called by the {@link IdentityManager} implementation to pass in the hex encoded a1 representation for validation against
     * the current request.
     *
     * The {@link Credential} is self validating based on the information passed in here, if verification is successful then the
     * {@link IdentityManager} can return the appropriate {@link Account} representation.
     *
     * @param ha1 - The hex encoded a1 value.
     * @return true if verification was successful, false otherwise.
     */
    boolean verifyHA1(final byte[] ha1);

    /**
     * Get the realm name the credential is being validated against.
     *
     * @return The realm name.
     */
    String getRealm();

    /**
     * If the algorithm is session based return the session data to be included when generating the ha1.
     *
     * @return The session data.
     * @throws IllegalStateException where the algorithm is not session based.
     */
    byte[] getSessionData();

}
