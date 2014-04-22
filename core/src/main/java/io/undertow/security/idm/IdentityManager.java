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
 * The IdentityManager interface to be implemented by an identity manager implementation providing user verification and
 * identity loading to Undertow.
 *
 * Note: The IdentityManager interface is very much work in progress, methods are added to cover use cases as identified and
 * then simplified as common cases are defined.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface IdentityManager {

    /**
     * Verify a previously authenticated account.
     *
     * Typical checks could be along the lines of verifying that the account is not now locked or that the password has not been
     * reset since last verified, also this provides an opportunity for roles to be re-loaded if membership information has
     * changed.
     *
     * @param account - The {@link Account} to verify.
     * @return An updates {@link Account} if verification is successful, null otherwise.
     */
    Account verify(final Account account);

    /**
     * Verify a supplied {@link Credential} against a requested ID.
     *
     * @param id - The requested ID for the account.
     * @param credential - The {@link Credential} to verify.
     * @return The {@link Account} for the user if verification was successful, null otherwise.
     */
    Account verify(final String id, final Credential credential);

    /**
     * Perform verification when all we have is the Credential, in this case the IdentityManager is also responsible for mapping the Credential to an account.
     *
     * The most common scenario for this would be mapping an X509Certificate to the user it is associated with.
     *
     * @param credential
     * @return
     */
    Account verify(final Credential credential);

}
