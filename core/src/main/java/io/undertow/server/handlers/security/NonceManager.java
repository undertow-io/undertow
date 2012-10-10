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

/**
 * A NonceManager is used by the HTTP Digest authentication mechanism to request nonces and to validate the nonces sent from the
 * client.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface NonceManager {

    // TODO - Should a nonce manager be able to tie these to a connection or session, or any other piece of info we have about
    // the client?
    // Also different rules depending on HTTP method or the resource being accessed?

    /**
     * Select the next nonce to be sent from the server taking into account the last valid nonce.
     *
     * @param lastNonce - The last valid nonce received from the client or null if we don't already have a nonce.
     * @return The next nonce to be sent in a challenge to the client.
     */
    String nextNonce(final String lastNonce);

    /**
     * Validate that a nonce can be used.
     *
     * If the nonce can not be used but the related digest was correct then a new nonce should be returned to the client
     * indicating that the nonce was stale.
     *
     * @param nonce - The nonce receieved from the client.
     * @param nonceCount - The nonce count from the client or -1 of none specified.
     * @return true if the nonce can be used otherwise return false.
     */
    boolean validateNonce(final String nonce, final int nonceCount);

}
