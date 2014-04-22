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

/**
 * An extension to the {@link NonceManager} interface for Nonce managers that also support the association of a pre-prepared
 * hash against a currently valid nonce.
 *
 * If the nonce manager replaces in-use nonces as old ones expire then the associated session hash should be migrated to the
 * replacement nonce.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface SessionNonceManager extends NonceManager {

    /**
     * Associate the supplied hash with the nonce specified.
     *
     * @param nonce - The nonce the hash is to be associated with.
     * @param hash - The hash to associate.
     */
    void associateHash(final String nonce, final byte[] hash);

    /**
     * Retrieve the existing hash associated with the nonce specified.
     *
     * If there is no association then null should be returned.
     *
     * @param nonce - The nonce the hash is required for.
     * @return The associated hash or null if there is no association.
     */
    byte[] lookupHash(final String nonce);

}
